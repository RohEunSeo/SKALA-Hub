"""관리자용 대화 로그 조회.

chat_logs 는 AI 서버가 소유한다(Spring 은 이 테이블을 모른다). 그래서 조회도 여기서 한다.

누가 관리자인지는 **DB의 users.role 만** 본다.
화면의 effectiveIsAdmin 은 '일반 모드 미리보기' 토글이 섞인 값이라 서버가 믿으면 안 된다.
"""
import logging
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query

from app.db import fetch_all
from app.routers.chat import current_user
from app.services import log_service

log = logging.getLogger(__name__)
router = APIRouter()

# 한 번에 가져올 수 있는 최대치. 더 필요하면 before 로 이어서 받는다.
MAX_LIMIT = 100

LOGS_SQL = """
SELECT l.id,
       l.created_at,
       l.user_slack_id,
       u.name  AS user_name,
       u.class_num,
       l.question,
       l.route,
       l.category,
       l.answer,
       l.abstained,
       l.llm_called,
       l.model,
       l.latency_ms,
       l.error,
       l.feedback,
       l.feedback_reason,
       l.retrieved_post_ids,
       l.retrieved_scores
FROM chat_logs l
LEFT JOIN users u ON u.slack_id = l.user_slack_id
WHERE (%(only_abstained)s::bool IS NOT TRUE OR l.abstained)
  AND (%(only_feedback)s::bool IS NOT TRUE OR l.feedback IS NOT NULL)
  AND (%(before)s::bigint IS NULL OR l.id < %(before)s)
ORDER BY l.id DESC
LIMIT %(limit)s
"""

# 오늘(KST) 요약. chat_logs.created_at 만 timestamptz 라 변환이 필요하다.
TODAY_SQL = """
SELECT count(*)                                   AS asked,
       count(*) FILTER (WHERE llm_called)         AS llm,
       count(DISTINCT user_slack_id)              AS users,
       count(*) FILTER (WHERE abstained)          AS abstained,
       count(*) FILTER (WHERE error IS NOT NULL)  AS errors,
       count(*) FILTER (WHERE feedback = 1)       AS up,
       count(*) FILTER (WHERE feedback = 0)       AS mid,
       count(*) FILTER (WHERE feedback = -1)      AS down,
       round(avg(latency_ms))                     AS avg_ms
FROM chat_logs
WHERE created_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul'
"""


def require_admin(slack_id: str = Depends(current_user)) -> str:
    """진짜 관리자만. 화면의 미리보기 토글과 무관하게 DB의 role 만 본다."""
    scope = log_service.user_scope(slack_id)
    if not scope or scope.get("role") != "admin":
        raise HTTPException(403, "관리자만 볼 수 있습니다")
    return slack_id


def _titles(post_ids: list[int] | None) -> dict[int, str]:
    """검색돼 온 글의 제목. '무엇을 찾아왔는지'가 실패 원인을 가린다."""
    if not post_ids:
        return {}
    rows = fetch_all(
        "SELECT id, coalesce(ai_title, left(content, 60)) AS t FROM posts WHERE id = ANY(%(ids)s)",
        {"ids": list(post_ids)},
    )
    return {r["id"]: r["t"] for r in rows}


@router.get("/admin/logs")
def logs(
    _: str = Depends(require_admin),
    limit: int = Query(30, ge=1, le=MAX_LIMIT),
    before: int | None = Query(None, description="이 id보다 작은 것만 - 더 보기"),
    only_abstained: bool = False,
    only_feedback: bool = False,
) -> dict[str, Any]:
    rows = fetch_all(
        LOGS_SQL,
        {
            "limit": limit,
            "before": before,
            "only_abstained": only_abstained or None,
            "only_feedback": only_feedback or None,
        },
    )

    # 제목은 한 번에 모아 조회한다 (행마다 쿼리하면 30번이 된다)
    all_ids = {pid for r in rows for pid in (r["retrieved_post_ids"] or [])}
    titles = _titles(sorted(all_ids))

    items = []
    for r in rows:
        ids = r["retrieved_post_ids"] or []
        scores = r["retrieved_scores"] or []
        items.append({
            "id": r["id"],
            "at": r["created_at"].isoformat() if r["created_at"] else None,
            "user": r["user_name"] or r["user_slack_id"],
            "classNum": r["class_num"],
            "question": r["question"],
            "route": r["route"],
            "category": r["category"],
            "answer": r["answer"],
            "abstained": r["abstained"],
            "llmCalled": r["llm_called"],
            "model": r["model"],
            "latencyMs": r["latency_ms"],
            "error": r["error"],
            "feedback": r["feedback"],
            "feedbackReason": r["feedback_reason"],
            "sources": [
                {"id": pid, "title": titles.get(pid, f"(글 {pid})"),
                 "score": round(float(scores[i]), 3) if i < len(scores) else None}
                for i, pid in enumerate(ids)
            ],
        })

    return {"items": items, "today": fetch_all(TODAY_SQL)[0]}
