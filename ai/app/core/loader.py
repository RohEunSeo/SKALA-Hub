"""RAG 0단계: 슬랙 게시글을 불러와 검색 가능한 청크로 분할.

여기엔 AI가 없다. DB에서 읽고 → 마크업 정리하고 → metadata 붙이고 → 자르는 것뿐이다.

Baseline에서 **일부러 뺀 것**
- 댓글: Baseline이 가장 단순해야 "댓글 덕분에 좋아졌다"를 말할 수 있다 (S5 실험 ①)
- user_name: 모든 글에 "4기_판교_5반_OOO"가 붙으면 공통 패턴이라 검색 노이즈가 된다
"""
import re
from datetime import datetime
from typing import Any

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.db import fetch_all

BOT_NAME = "SKALA-Hub"  # 동기화 안내 봇. replies 436행 중 151행(35%)이 이것

# 슬랙에서 글을 지우면 본문이 이 문구로 바뀐다. SKALA Hub에는 제목·반응이 남아 있어 그대로 두지만,
# 검색에는 읽을 내용이 0이라 뺀다. (SlackSyncService가 삭제를 DB에 반영하지 않아 생기는 상황)
TOMBSTONE = re.compile(r"^\s*(this message was deleted\.?|이 메시지는 삭제되었습니다\.?)\s*$", re.I)

# 아래 세 숫자는 **아직 측정 안 된 출발점**이다 (S5 실험 ②에서 스윕해 확정).
# 수업 기본값 120/20은 교재 샘플(도서관 규정 같은 짧은 글) 기준이라 슬랙 글에 안 맞는다.
# 슬랙 글은 한 글이 하나의 의미 단위라 짧게 자르면 문맥이 끊긴다.
SHORT_DOC_CHARS = 500  # 이보다 짧으면 자르지 않고 통째로 한 청크
CHUNK_SIZE = 600
CHUNK_OVERLAP = 100

# posts + 커리큘럼 라벨 + 사람 댓글 수.
# reply_count는 posts 컬럼을 쓰지 않는다 - 슬랙 원본값이라 봇 댓글이 포함되고 값도 안 맞는다
# (post 170: DB 33 vs 실제 34). replies에서 봇을 뺀 수를 직접 센다.
POSTS_SQL = """
SELECT p.id, p.slack_ts, p.ai_title, p.content, p.category, p.tags,
       p.reaction_count, p.created_at,
       c.stage, c.sub_category,
       COALESCE(h.cnt, 0) AS reply_count
FROM posts p
LEFT JOIN curriculum_posts c
       ON c.post_id = p.id AND c.is_excluded = false
LEFT JOIN (
    SELECT post_id, count(*) AS cnt
    FROM replies
    WHERE user_name <> %(bot)s
    GROUP BY post_id
) h ON h.post_id = p.id
WHERE p.is_deleted = false
  AND (COALESCE(p.content, '') <> '' OR p.ai_title IS NOT NULL)
ORDER BY p.id
"""

# 슬랙 마크업 - 문서화된 고정 형식이라 정규화는 튜닝이 아니라 파싱이다.
# 프론트도 표시할 때 같은 변환을 한다 (CLAUDE.md 「게시글 표시 방식」).
_LINK_LABELED = re.compile(r"<(https?://[^|>]+)\|([^>]*)>")  # <url|라벨> - 222개 글
_LINK_BARE = re.compile(r"<(https?://[^|>]+)>")
_MENTION = re.compile(r"<@[A-Z0-9]+(?:\|[^>]*)?>")           # <@U123|이름> - 실명이 들어있다
_CHANNEL = re.compile(r"<#[A-Z0-9]+\|([^>]*)>")
_EMOJI = re.compile(r":[a-z0-9_+-]+:")                       # :white_check_mark: - 492회
_BOLD = re.compile(r"\*([^*\n]+)\*")                         # *굵게* - 570회
_BLANKS = re.compile(r"\n{3,}")


def clean_slack_text(text: str | None) -> str:
    """슬랙 마크업을 사람이 읽는 형태로 되돌린다.

    <url|라벨>은 라벨만 남긴다. 라벨이 URL을 그대로 반복하는 글이 많은데
    (예: <https://a.com/study-jams|a.com/study-jams>), 경로에 의미 있는 단어가
    섞여 있어서 통째로 버리지 않고 한 번만 남긴다.
    """
    if not text:
        return ""
    t = _LINK_LABELED.sub(lambda m: m.group(2) or m.group(1), text)
    t = _LINK_BARE.sub(lambda m: m.group(1), t)
    t = _CHANNEL.sub(lambda m: m.group(1), t)
    t = _MENTION.sub("", t)  # 멘션은 실명이라 지운다 (user_name을 뺀 것과 같은 이유)
    t = _EMOJI.sub("", t)
    t = _BOLD.sub(lambda m: m.group(1), t)
    return _BLANKS.sub("\n\n", t).strip()


def _to_metadata(row: dict[str, Any]) -> dict[str, Any]:
    """검색 결과를 화면에 그릴 때와 필터링할 때 쓸 값들.

    created_at은 ISO 문자열로 둔다 - FAISS는 pickle이라 datetime도 되지만
    pgvector(JSONB)로 옮길 때 그대로 쓰려면 문자열이 안전하다.
    """
    created = row.get("created_at")
    return {
        "post_id": row["id"],
        "slack_ts": row.get("slack_ts") or "",
        "ai_title": row.get("ai_title") or "",
        "category": row.get("category") or "",
        "tags": list(row.get("tags") or []),
        "created_at": created.isoformat() if isinstance(created, datetime) else "",
        "reaction_count": row.get("reaction_count") or 0,
        "reply_count": row.get("reply_count") or 0,
        "stage": row.get("stage") or "",            # 커리큘럼 탭 필터용
        "sub_category": row.get("sub_category") or "",
    }


def load_posts() -> tuple[list[Document], list[int]]:
    """게시글 한 건 = Document 한 개 (아직 자르지 않은 상태).

    반환: (문서 목록, 건너뛴 [post_id, 이유] 목록)
    건너뛴 글을 같이 돌려주는 이유 - 글이 소리 없이 사라지면 나중에 Hit@K가 왜 낮은지
    추적할 수 없다.
    """
    docs: list[Document] = []
    skipped: list[tuple[int, str]] = []
    for row in fetch_all(POSTS_SQL, {"bot": BOT_NAME}):
        raw = row.get("content") or ""
        if TOMBSTONE.match(raw):
            skipped.append((row["id"], "슬랙에서 삭제됨"))  # 사이트에는 그대로 남는다
            continue
        body = clean_slack_text(raw)
        title = (row.get("ai_title") or "").strip()
        # 제목을 본문 앞에 붙인다. 본문이 비고 제목만 있는 글도 검색에 걸리게 된다.
        text = f"{title}\n\n{body}".strip() if title else body
        if not text:
            skipped.append((row["id"], "본문·제목 없음"))  # 이미지만 올린 글. OCR은 범위 밖
            continue
        docs.append(Document(page_content=text, metadata=_to_metadata(row)))
    return docs, skipped


def split_documents(docs: list[Document]) -> list[Document]:
    """짧은 글은 그대로, 긴 글만 자른다."""
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
    )
    chunks: list[Document] = []
    for doc in docs:
        pieces = [doc] if len(doc.page_content) < SHORT_DOC_CHARS else splitter.split_documents([doc])
        for i, piece in enumerate(pieces):
            # 검색 결과에 같은 post_id가 여러 개 나올 때 어느 부분인지 알 수 있게
            piece.metadata = {**piece.metadata, "chunk_index": i, "chunk_total": len(pieces)}
            chunks.append(piece)
    return chunks


def load_chunks() -> list[Document]:
    """인덱싱에 바로 넣을 수 있는 최종 청크 목록."""
    docs, _ = load_posts()
    return split_documents(docs)


def main() -> None:
    """눈으로 확인하는 용도: uv run python -m app.core.loader"""
    docs, skipped = load_posts()
    chunks = split_documents(docs)
    lengths = sorted(len(c.page_content) for c in chunks)
    split_count = sum(1 for d in docs if len(d.page_content) >= SHORT_DOC_CHARS)

    print(f"글 {len(docs)}개 → 조각 {len(chunks)}개")
    print(f"  자른 글 {split_count}개 / 통째로 둔 글 {len(docs) - split_count}개")
    print(f"  조각 길이: 최소 {lengths[0]}  중앙값 {lengths[len(lengths) // 2]}  최대 {lengths[-1]}")
    print(f"  커리큘럼 라벨이 붙은 조각: {sum(1 for c in chunks if c.metadata['stage'])}개")
    for pid, why in skipped:
        print(f"  건너뜀: post {pid} ({why})")

    print("\n--- 샘플 3개 ---")
    for c in chunks[:3]:
        m = c.metadata
        print(f"\n[post {m['post_id']}] {m['category']} {m['tags']} "
              f"👍{m['reaction_count']} 💬{m['reply_count']} ({m['chunk_index'] + 1}/{m['chunk_total']})")
        print("  " + c.page_content[:180].replace("\n", " "))


if __name__ == "__main__":
    main()
