"""세고 정렬하는 일. 벡터 검색도 LLM도 쓰지 않는다.

벡터 검색은 상위 k개만 돌려주므로 "총 몇 개", "가장 반응 많은 글" 같은 질문에
구조적으로 답할 수 없다. 5개만 보고 268개 중 1등을 말하면 자신 있게 틀린다.
그래서 이런 질문은 DB에 직접 묻는다.
"""
from app.db import fetch_all

# 챗봇이 한 번에 보여줄 글 수. 검색 경로(k=5)와 맞춘다.
DEFAULT_LIMIT = 5


def popular_posts(days: int | None = 30, limit: int = DEFAULT_LIMIT) -> list[dict]:
    """반응 많은 순. days=None이면 전체 기간.

    반응 수가 같으면 최신 글을 앞에 둔다 - 오래된 글만 계속 1등으로 뜨는 걸 막는다.
    """
    rows = fetch_all(
        """
        SELECT id, ai_title, content, category, reaction_count
        FROM posts
        WHERE is_deleted = false
          AND (%(days)s::int IS NULL
               OR created_at >= now() - make_interval(days => %(days)s::int))
        ORDER BY reaction_count DESC, created_at DESC
        LIMIT %(limit)s
        """,
        {"days": days, "limit": limit},
    )
    return [_to_source(r) for r in rows]


def recent_posts(limit: int = DEFAULT_LIMIT) -> list[dict]:
    """최신순."""
    rows = fetch_all(
        """
        SELECT id, ai_title, content, category, reaction_count
        FROM posts
        WHERE is_deleted = false
        ORDER BY created_at DESC
        LIMIT %(limit)s
        """,
        {"limit": limit},
    )
    return [_to_source(r) for r in rows]


def post_count(days: int | None = None) -> int:
    """글 개수. "총 몇 개야?" 류에 쓴다."""
    row = fetch_all(
        """
        SELECT count(*) AS n FROM posts
        WHERE is_deleted = false
          AND (%(days)s::int IS NULL
               OR created_at >= now() - make_interval(days => %(days)s::int))
        """,
        {"days": days},
    )[0]
    return int(row["n"])


def _to_source(row: dict) -> dict:
    """화면이 추천 카드를 그릴 때 쓰는 모양으로 맞춘다.

    rag_service._to_sources()와 같은 필드여야 한다 - 화면은 어느 길로 왔는지 모른다.
    """
    title = (row.get("ai_title") or "").strip()
    if not title:
        title = (row.get("content") or "").split("\n")[0][:60]
    return {
        "id": row["id"],
        "title": title,
        "category": row.get("category") or "",
        "reactions": row.get("reaction_count") or 0,
    }
