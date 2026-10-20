"""RAG 1단계: 글을 숫자로 바꿔 보관하고, 질문과 비슷한 조각을 찾아온다.

임베딩 모델과 벡터 저장소를 만드는 곳은 **여기 하나뿐**이다.
나중에 pgvector로 옮길 때 다른 파일은 건드리지 않는다 (CLAUDE.md 「설계 판단 기준」).

    VECTOR_STORE=faiss      평가용 - 파일로 얼려 두면 점수 비교가 흔들리지 않는다
    VECTOR_STORE=pgvector   실사용 - 필터 결합·갱신·재배포에 유리 (아직 미구현)
"""
from functools import lru_cache

from langchain_core.documents import Document
from langchain_core.vectorstores import VectorStore
from langchain_openai import OpenAIEmbeddings

from app.config import settings
from app.db import fetch_all, get_connection


def get_embeddings() -> OpenAIEmbeddings:
    """임베딩 모델을 만드는 유일한 곳.

    생성 모델과 달리 이걸 바꾸면 **전체 재색인**이 필요하다. 가볍게 바꾸지 말 것.
    """
    settings.require("openai_api_key")
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.openai_api_key,
    )


def _unsupported(name: str) -> VectorStore:
    raise NotImplementedError(
        f"VECTOR_STORE={name} 는 아직 구현하지 않았습니다. "
        "지금은 faiss만 됩니다 (.env의 VECTOR_STORE 확인)."
    )


def build_index(chunks: list[Document]) -> int:
    """조각들을 숫자로 바꿔 저장한다. 비용이 드는 건 여기뿐이다. 반환: 저장한 조각 수."""
    if settings.vector_store == "pgvector":
        return _pg_build(chunks)
    if settings.vector_store != "faiss":
        _unsupported(settings.vector_store)

    from langchain_community.vectorstores import FAISS

    store = FAISS.from_documents(chunks, get_embeddings())
    settings.faiss_dir.parent.mkdir(parents=True, exist_ok=True)
    store.save_local(str(settings.faiss_dir))
    return len(chunks)


# --- pgvector ---------------------------------------------------------------
# 테이블은 backend/post_chunks.sql 이 만든다.
# LangChain의 PGVector 클래스를 쓰지 않는 이유 둘:
#  1) 그 클래스는 metadata를 JSONB 덩어리로 넣는데, 우리는 category를 **컬럼**으로 둬야
#     "필터 + 유사도"를 한 쿼리에서 빠르게 할 수 있다. 그게 pgvector를 쓰는 이유 자체다
#  2) 나중에 Spring 동기화가 새 글의 임베딩을 직접 채울 계획인데,
#     LangChain이 테이블 모양을 정해버리면 끼어들기 어렵다
# 호출부는 search_with_scores()만 보므로 안을 갈아끼워도 다른 파일은 바뀌지 않는다.

_INSERT_SQL = """
INSERT INTO post_chunks (post_id, chunk_index, chunk_total, content, embedding)
VALUES (%(post_id)s, %(chunk_index)s, %(chunk_total)s, %(content)s, %(embedding)s)
ON CONFLICT (post_id, chunk_index) DO UPDATE SET
    content = EXCLUDED.content, embedding = EXCLUDED.embedding, indexed_at = now()
"""

# 분류·태그·반응수는 **여기서 그때그때 읽는다**(복사해두지 않는다).
# 관리자가 /admin에서 분류를 고쳐도 자동 반영되고, is_deleted로 지운 글은 저절로 빠진다.
# 커리큘럼 라벨도 loader와 같은 조건(is_excluded=false)으로 붙인다.
#
# <=> 는 코사인 거리다 (0이면 같은 방향, 1이면 무관, 2면 정반대).
# FAISS의 L2 거리와 눈금이 다르다 - 그래서 로그에 index_version을 같이 남긴다.
#
# **k개를 세는 단위는 '조각'이 아니라 '글'이다** (DISTINCT ON).
# 조각으로 세면 한 글의 조각들이 상위를 독식해 칸이 날아간다. 화면은 _to_sources()에서
# 중복을 걸러내므로 같은 글이 두 번 뜨지는 않지만, 그만큼 **카드 수가 줄어든다.**
# 실측(10-16, 평가셋 42문항): 상위 5개의 서로 다른 글이 평균 4.12개.
# 최악은 R02 "학습노트 퀴즈" - 5칸 전부 글 131이라 카드가 1개만 나왔다.
# loader가 조각마다 제목을 붙인 뒤로 한 글의 조각들이 서로 닮아져 더 심해졌다
# (같은 측정: 4.54 → 4.12, 칸을 낭비한 질문 9개 → 14개).
_SEARCH_SQL = """
SELECT * FROM (
    SELECT DISTINCT ON (c.post_id)
           c.post_id, c.chunk_index, c.chunk_total, c.content,
           p.category, p.tags, p.ai_title, p.slack_ts,
           p.reaction_count, p.created_at AS post_created_at,
           cur.stage, cur.sub_category,
           c.embedding <=> %(q)s::vector AS distance
    FROM post_chunks c
    JOIN posts p ON p.id = c.post_id AND p.is_deleted = false
    LEFT JOIN curriculum_posts cur
           ON cur.post_id = c.post_id AND cur.is_excluded = false
    WHERE (%(category)s::varchar IS NULL OR p.category = %(category)s)
    ORDER BY c.post_id, c.embedding <=> %(q)s::vector
) best
ORDER BY distance
LIMIT %(k)s
"""


def _vector_literal(values: list[float]) -> str:
    """psycopg는 vector 타입을 모른다. '[0.1,0.2,...]' 문자열로 넘기고 ::vector로 캐스팅한다."""
    return "[" + ",".join(f"{v:.8f}" for v in values) + "]"


def _chunk_rows(chunks: list[Document]) -> list[dict]:
    """조각들을 임베딩하고 INSERT에 바로 쓸 수 있는 dict로 바꾼다."""
    vectors = get_embeddings().embed_documents([c.page_content for c in chunks])
    return [
        {
            "post_id": c.metadata["post_id"],
            "chunk_index": c.metadata.get("chunk_index", 0),
            "chunk_total": c.metadata.get("chunk_total", 1),
            "content": c.page_content,
            "embedding": _vector_literal(v),
        }
        for c, v in zip(chunks, vectors)
    ]


def _pg_build(chunks: list[Document]) -> int:
    """글 **전체**를 통째로 다시 임베딩해서 post_chunks에 넣는다.

    ⚠️ **TRUNCATE로 테이블을 통째로 비운 뒤 다시 채운다.** 전체 재색인
    (scripts/build_index.py) 전용이다 - 글 1건만 반영하려고 이 함수를 여러 번
    부르면, 부를 때마다 테이블이 비워져서 **마지막으로 부른 글 1건만 남고
    나머지 전부가 사라진다** (실제로 이렇게 사고가 났다, 10-19).
    글 1건만 반영하려면 pg_upsert_post()를 쓴다.
    """
    rows = _chunk_rows(chunks)
    with get_connection() as conn, conn.cursor() as cur:
        # 지운 글의 조각이 남지 않게 통째로 비우고 다시 넣는다.
        # 412개 규모에선 증분보다 이게 단순하고 안전하다 (증분은 글이 수만 개일 때 의미가 있다).
        cur.execute("TRUNCATE post_chunks")
        cur.executemany(_INSERT_SQL, rows)
    return len(rows)


def pg_upsert_post(chunks: list[Document]) -> int:
    """글 **한 건**의 조각만 반영한다. core/indexer.py가 새 글 1건을 색인할 때 쓴다.

    _pg_build()와 달리 TRUNCATE하지 않는다 - 이 글의 기존 조각만 지우고 새로
    넣어서 다른 글은 전혀 건드리지 않는다. 글이 짧아져 조각 수가 줄어도
    (예: 3개 → 2개) 남은 조각이 생기지 않게, 지우고 나서 다시 넣는다.
    """
    if not chunks:
        return 0
    post_id = chunks[0].metadata["post_id"]
    rows = _chunk_rows(chunks)
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM post_chunks WHERE post_id = %(post_id)s", {"post_id": post_id})
        cur.executemany(_INSERT_SQL, rows)
    return len(rows)


def _pg_rows_to_docs(rows: list[dict]) -> list[Document]:
    """DB 행을 FAISS 경로와 **똑같은 모양**의 Document로 되돌린다.

    모양이 다르면 rag_service·generator가 저장소에 따라 다르게 동작하게 된다.
    """
    docs = []
    for r in rows:
        created = r.get("post_created_at")
        docs.append(Document(
            page_content=r["content"],
            metadata={
                "post_id": r["post_id"],
                "slack_ts": r.get("slack_ts") or "",
                "ai_title": r.get("ai_title") or "",
                "category": r.get("category") or "",
                "tags": list(r.get("tags") or []),
                "created_at": created.isoformat() if created else "",
                "reaction_count": r.get("reaction_count") or 0,
                "reply_count": 0,  # 사람 댓글 수는 검색에 쓰지 않는다 (loader가 따로 세는 값)
                "stage": r.get("stage") or "",
                "sub_category": r.get("sub_category") or "",
                "chunk_index": r.get("chunk_index", 0),
                "chunk_total": r.get("chunk_total", 1),
                "score": float(r["distance"]),
            },
        ))
    return docs


def _pg_search(question: str, k: int, category: str | None) -> list[Document]:
    """필터와 유사도를 한 쿼리에서 처리한다.

    FAISS는 '전체에서 가까운 fetch_k개를 뽑고 거른다'라서, 그 안에 못 들면 놓친다.
    여기서는 해당 카테고리 전체를 보고 고르므로 빠뜨리지 않는다.
    """
    q = _vector_literal(get_embeddings().embed_query(question))
    rows = fetch_all(_SEARCH_SQL, {"q": q, "category": category, "k": k})
    return _pg_rows_to_docs(rows)


@lru_cache(maxsize=1)
def load_index() -> VectorStore:
    """저장해 둔 인덱스를 불러온다. 질문할 때마다 다시 임베딩하지 않기 위해서다.

    프로세스마다 한 번만 읽는다(lru_cache). 캐시가 없으면 질문마다 디스크에서 다시 읽어
    불필요하게 느려진다. 대신 **인덱스를 다시 만들면 서버를 재시작**해야 반영된다
    - 운영 중 인덱스가 바뀌는 건 오히려 사고라서, 고정되는 쪽이 맞다.
    """
    if settings.vector_store != "faiss":
        return _unsupported(settings.vector_store)

    from langchain_community.vectorstores import FAISS

    if not settings.faiss_dir.exists():
        raise FileNotFoundError(
            f"인덱스가 없습니다: {settings.faiss_dir}\n"
            "먼저 만들어 주세요:  uv run python -m scripts.build_index"
        )
    # 우리가 만든 파일만 읽으므로 pickle 로드를 허용한다 (조각 원문이 .pkl에 들어있다)
    return FAISS.load_local(
        str(settings.faiss_dir), get_embeddings(),
        allow_dangerous_deserialization=True,
    )


# FAISS는 metadata로 거른 뒤 k개를 채우려고 fetch_k개를 먼저 본다.
# 이 값이 작으면 "걸렀더니 2개밖에 안 남는" 일이 생긴다.
# (pgvector로 가면 WHERE 절로 한 번에 처리되어 이 보정 자체가 없어진다)
FETCH_MULTIPLIER = 20


# 글 단위로 줄이기 전에 조각을 몇 배로 받아올지. 한 글이 조각 5개까지 나는 걸 봤으니
# 3배면 최악에도 서로 다른 글 k개를 채운다. pgvector는 SQL이 한 번에 처리해 이 값이 없다.
DEDUPE_MULTIPLIER = 3


def _search_kwargs(k: int, category: str | None) -> dict:
    """검색 조건을 만드는 곳. 동기/비동기가 같은 조건을 쓰게 하려고 분리했다.

    k개가 아니라 k*3개를 요청한다 - _best_per_post()가 글 단위로 줄일 재료가 필요하다.
    """
    kwargs: dict = {"k": k * DEDUPE_MULTIPLIER}
    if category:
        kwargs["filter"] = {"category": category}
        kwargs["fetch_k"] = k * DEDUPE_MULTIPLIER * FETCH_MULTIPLIER
    return kwargs


def _best_per_post(docs: list[Document], k: int) -> list[Document]:
    """글마다 가장 가까운 조각 하나만 남기고 앞에서 k개. (pgvector의 DISTINCT ON과 같은 일)

    docs는 이미 가까운 순이라 먼저 나온 조각이 그 글의 1등이다.
    """
    seen: set = set()
    out: list[Document] = []
    for doc in docs:
        pid = doc.metadata.get("post_id")
        if pid in seen:
            continue
        seen.add(pid)
        out.append(doc)
        if len(out) == k:
            break
    return out


def _with_scores(pairs) -> list[Document]:
    """(조각, 거리) 쌍을 조각 목록으로 바꾸고 거리를 metadata["score"]에 넣는다.

    원래 쓰던 `as_retriever()`는 **거리를 버린다.** 그런데 이 값이 필요하다
    - "상위 5개 중 4~5위에 노이즈가 섞인다"를 고치려면 점수가 어디서 뚝 떨어지는지
      실제 분포를 봐야 하고, 그 재료가 로그에 남는 이 값이다.

    FAISS가 주는 건 **L2 거리**다 (작을수록 가까움). 정규화된 벡터에서
    cosine = 1 - 거리^2/2 로 환산해 읽는다. 여기서는 환산하지 않고 원값을 그대로 담는다.
    """
    out = []
    for doc, distance in pairs:
        doc.metadata = {**doc.metadata, "score": float(distance)}
        out.append(doc)
    return out


def search_with_scores(question: str, k: int = 5, category: str | None = None) -> list[Document]:
    """질문과 비슷한 조각 k개. category를 주면 그 카테고리 안에서만 찾는다.

    벡터 저장소를 직접 부르는 곳은 이 함수와 아래 비동기 쌍둥이뿐이다.
    pgvector로 옮길 때 고칠 곳도 여기까지다.
    """
    if settings.vector_store == "pgvector":
        return _pg_search(question, k, category)
    if settings.vector_store != "faiss":
        _unsupported(settings.vector_store)
    pairs = load_index().similarity_search_with_score(question, **_search_kwargs(k, category))
    return _best_per_post(_with_scores(pairs), k)


async def asearch_with_scores(
    question: str, k: int = 5, category: str | None = None
) -> list[Document]:
    """search_with_scores의 비동기 버전. 서버(FastAPI)는 이쪽을 쓴다."""
    if settings.vector_store == "pgvector":
        # DB 왕복이라 동기 함수지만, 412행 조회라 수 밀리초다. 체감 차이가 없어 그대로 쓴다.
        return _pg_search(question, k, category)
    if settings.vector_store != "faiss":
        _unsupported(settings.vector_store)
    pairs = await load_index().asimilarity_search_with_score(
        question, **_search_kwargs(k, category)
    )
    return _best_per_post(_with_scores(pairs), k)
