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


def build_index(chunks: list[Document]) -> VectorStore:
    """조각들을 숫자로 바꿔 인덱스를 만들고 저장한다. 비용이 드는 건 여기뿐이다."""
    if settings.vector_store != "faiss":
        return _unsupported(settings.vector_store)

    from langchain_community.vectorstores import FAISS

    store = FAISS.from_documents(chunks, get_embeddings())
    settings.faiss_dir.parent.mkdir(parents=True, exist_ok=True)
    store.save_local(str(settings.faiss_dir))
    return store


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


def _search_kwargs(k: int, category: str | None) -> dict:
    """검색 조건을 만드는 곳. 동기/비동기가 같은 조건을 쓰게 하려고 분리했다."""
    kwargs: dict = {"k": k}
    if category:
        kwargs["filter"] = {"category": category}
        kwargs["fetch_k"] = k * FETCH_MULTIPLIER
    return kwargs


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
    if settings.vector_store != "faiss":
        _unsupported(settings.vector_store)
    pairs = load_index().similarity_search_with_score(question, **_search_kwargs(k, category))
    return _with_scores(pairs)


async def asearch_with_scores(
    question: str, k: int = 5, category: str | None = None
) -> list[Document]:
    """search_with_scores의 비동기 버전. 서버(FastAPI)는 이쪽을 쓴다."""
    if settings.vector_store != "faiss":
        _unsupported(settings.vector_store)
    pairs = await load_index().asimilarity_search_with_score(
        question, **_search_kwargs(k, category)
    )
    return _with_scores(pairs)
