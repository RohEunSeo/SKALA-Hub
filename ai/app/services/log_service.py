"""챗봇 질문 로그 + 일일 한도 + 베타 공개 범위.

`chat_logs`에 쓰는 곳은 여기 하나다 (테이블은 backend/chat_logs.sql).

**원칙: 로그는 답변을 막지 않는다.**
- 저장이 실패해도 조용히 넘긴다 - 답변은 이미 사용자에게 다 갔다
- 한도 조회가 실패하면 **통과시킨다** - DB가 흔들려서 챗봇이 아예 안 되는 쪽이 더 나쁘다
로그는 부가기능이고 답변이 본론이다.
"""
import logging
from typing import Any

from app.config import settings
from app.db import fetch_all, get_connection

log = logging.getLogger(__name__)

# "하루"는 한국 시간 기준이다. UTC로 세면 한국 오전 9시에 한도가 리셋된다.
# date_trunc 결과를 다시 timestamptz로 되돌려 비교하므로 (user_slack_id, created_at) 인덱스를 탄다.
_TODAY_START = "date_trunc('day', now() at time zone 'Asia/Seoul') at time zone 'Asia/Seoul'"

USED_TODAY_SQL = f"""
SELECT count(*) AS n
FROM chat_logs
WHERE user_slack_id = %(u)s
  AND llm_called
  AND created_at >= {_TODAY_START}
"""

SCOPE_SQL = "SELECT campus, class_num, role FROM users WHERE slack_id = %(u)s"

INSERT_SQL = """
INSERT INTO chat_logs (
    user_slack_id, question, route, category,
    retrieved_post_ids, retrieved_scores,
    answer, abstained,
    llm_called, model, index_version, latency_ms,
    prompt_tokens, completion_tokens, error
) VALUES (
    %(user_slack_id)s, %(question)s, %(route)s, %(category)s,
    %(retrieved_post_ids)s, %(retrieved_scores)s,
    %(answer)s, %(abstained)s,
    %(llm_called)s, %(model)s, %(index_version)s, %(latency_ms)s,
    %(prompt_tokens)s, %(completion_tokens)s, %(error)s
) RETURNING id
"""

# 자기 로그에만 피드백을 달 수 있다. user_slack_id 조건이 그 확인을 겸한다.
FEEDBACK_SQL = """
UPDATE chat_logs SET feedback = %(v)s, feedback_reason = %(r)s, feedback_at = now()
WHERE id = %(id)s AND user_slack_id = %(u)s
"""


def user_scope(slack_id: str) -> dict[str, Any] | None:
    """베타 공개 범위를 판정할 때 쓸 유저 정보. 없는 유저면 None."""
    rows = fetch_all(SCOPE_SQL, {"u": slack_id})
    return rows[0] if rows else None


def can_use_chat(slack_id: str, scope: dict[str, Any] | None) -> bool:
    """이 유저에게 챗봇을 열어줄까.

    통과하는 경우는 셋이다.
    1. 관리자 - 항상 (일반 모드 미리보기로 사용자 화면을 확인해야 하므로)
    2. CHAT_ALLOWED_USERS에 지목된 사람 - 매니저·교수님은 DB에 campus/class_num이
       비어 있어 반 기준으로는 통과할 수 없다
    3. 허용된 캠퍼스 + 반

    설정이 비어 있으면 제한 없음 - 340명 오픈은 .env 두 줄을 지우는 일이다.
    """
    if scope is None:
        return False
    if scope.get("role") == "admin":
        return True
    if slack_id in settings.allowed_users:
        return True

    campus = settings.chat_allowed_campus.strip()
    if campus and scope.get("campus") != campus:
        return False

    classes = settings.allowed_classes
    if classes and scope.get("class_num") not in classes:
        return False
    return True


def is_unlimited(scope: dict[str, Any] | None) -> bool:
    """하루 한도를 적용하지 않을 사람.

    관리자는 면제한다 - 기능을 확인하려면 하루에 수십 번 물어봐야 하는데,
    10번에 막히면 테스트 자체가 안 된다. 횟수는 면제해도 **로그는 그대로 남으므로**
    누가 얼마나 썼는지는 chat_logs에서 그대로 보인다(비용이 숨지 않는다).
    """
    return bool(scope) and scope.get("role") == "admin"


def used_today(slack_id: str) -> int:
    """오늘 LLM을 부른 횟수. 조회가 실패하면 0을 돌려줘 통과시킨다."""
    try:
        return fetch_all(USED_TODAY_SQL, {"u": slack_id})[0]["n"]
    except Exception:
        log.warning("사용량 조회 실패 - 한도를 적용하지 않고 통과시킵니다", exc_info=True)
        return 0


def save(
    *,
    slack_id: str,
    question: str,
    route: str | None = None,
    category: str | None = None,
    post_ids: list[int] | None = None,
    scores: list[float] | None = None,
    answer: str | None = None,
    abstained: bool = False,
    llm_called: bool = False,
    model: str | None = None,
    latency_ms: int | None = None,
    prompt_tokens: int | None = None,
    completion_tokens: int | None = None,
    error: str | None = None,
) -> int | None:
    """로그 한 건. 실패하면 None을 돌려주고 조용히 넘어간다.

    키워드 인수만 받는다(`*`) - 컬럼이 15개라 위치 인수로는 순서를 틀리기 쉽다.
    """
    params = {
        "user_slack_id": slack_id,
        "question": question,
        "route": route,
        "category": category,
        "retrieved_post_ids": post_ids or None,
        "retrieved_scores": scores or None,
        "answer": answer,
        "abstained": abstained,
        "llm_called": llm_called,
        # 폴백이 걸리면 설정값과 다를 수 있다. 실제 응답 모델을 우선한다.
        "model": (model or settings.llm_model) if llm_called else None,
        "index_version": settings.index_version,
        "latency_ms": latency_ms,
        "prompt_tokens": prompt_tokens,
        "completion_tokens": completion_tokens,
        "error": error,
    }
    try:
        with get_connection() as conn, conn.cursor() as cur:
            cur.execute(INSERT_SQL, params)
            return cur.fetchone()["id"]
    except Exception:
        log.warning("로그 저장 실패 (q=%r)", question[:40], exc_info=True)
        return None


def set_feedback(log_id: int, slack_id: str, value: int, reason: str | None = None) -> bool:
    """👍(1)/👎(-1). 남의 로그에는 달 수 없다(WHERE에 user_slack_id가 있다).

    👎는 두 번 들어온다 - 누를 때 한 번, 이유를 고를 때 한 번. 같은 행을 덮어쓴다.
    """
    if value not in (1, -1):
        return False
    try:
        with get_connection() as conn, conn.cursor() as cur:
            cur.execute(FEEDBACK_SQL, {"id": log_id, "u": slack_id, "v": value, "r": reason})
            return cur.rowcount > 0
    except Exception:
        log.warning("피드백 저장 실패 (log_id=%s)", log_id, exc_info=True)
        return False
