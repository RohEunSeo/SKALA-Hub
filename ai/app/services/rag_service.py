"""RAG 파이프라인 조립: 검색 → 프롬프트 → 생성.

검색을 체인 안에 넣지 않고 밖에서 먼저 하는 이유:
화면에 추천 카드를 그리려면 **어떤 글을 썼는지**가 필요한데,
체인 안에서 검색하면 그 목록을 꺼낼 수 없다.
"""
from typing import AsyncIterator

from langchain_core.documents import Document
from langchain_core.output_parsers import StrOutputParser

from app.core.generator import PROMPT, SUMMARY_PROMPT, format_docs, get_llm
from app.core.retriever import asearch_with_scores, search_with_scores

DEFAULT_K = 5  # 몇 개를 근거로 줄지. 늘리면 비용↑ + 중간 글을 놓치기 쉬움 (S5에서 스윕)


def answer(question: str, k: int = DEFAULT_K) -> dict:
    """질문 하나에 답한다.

    반환: {"answer": 답변 글, "sources": [{post_id, title, category, reaction_count}, ...]}
    sources는 화면의 추천 카드와 Step 6의 Hit@K 측정에 함께 쓰인다.
    """
    docs = search_with_scores(question, k)

    chain = PROMPT | get_llm() | StrOutputParser()
    text = chain.invoke({"context": format_docs(docs), "question": question})

    return {"answer": text.strip(), "sources": _to_sources(docs)}


def _to_sources(docs: list[Document]) -> list[dict]:
    """검색된 조각을 화면용 글 목록으로. 같은 글의 조각이 여럿이면 한 번만."""
    seen, out = set(), []
    for doc in docs:
        m = doc.metadata
        if m["post_id"] in seen:
            continue
        seen.add(m["post_id"])
        out.append({
            "id": m["post_id"],
            "title": m.get("ai_title") or doc.page_content.split("\n")[0][:60],
            "category": m.get("category") or "",
            "reactions": m.get("reaction_count", 0),
        })
    return out


async def search(question: str, k: int = DEFAULT_K, category: str | None = None) -> list[Document]:
    """검색만. 답변을 만들기 전에 '어떤 글을 쓸지' 먼저 화면에 보여주려고 분리했다.

    category가 있으면 그 카테고리 안에서만 찾는다. 못 찾으면 전체에서 다시 찾는다
    - 탭 안에 답이 없다고 "없어요"만 하면 쓸모가 없기 때문.
    """
    docs = await asearch_with_scores(question, k, category)
    if not docs and category:
        docs = await asearch_with_scores(question, k)
    return docs


async def stream_answer(
    question: str, docs: list[Document], tracker=None
) -> AsyncIterator[str]:
    """답변을 조각조각 흘려보낸다.

    answer()처럼 통째로 기다리면 2초간 화면이 멈춘다.
    화면은 조각을 이어붙이므로(current.text += ev.text) **차이분만** 넘긴다.

    tracker: generator.ModelTracker. 폴백이 걸렸을 때 **실제로 응답한 모델**을 받아온다.
    """
    chain = PROMPT | get_llm() | StrOutputParser()
    config = {"callbacks": [tracker]} if tracker else {}
    async for piece in chain.astream(
        {"context": format_docs(docs), "question": question}, config=config
    ):
        if piece:
            yield piece


async def stream_summary(content: str, tracker=None) -> AsyncIterator[str]:
    """글 하나를 요약해 흘려보낸다. **검색을 거치지 않는다.**

    검색 경로(stream_answer)와 나란히 두는 이유 - 둘 다 "LLM을 부르는 곳"이라
    모델 교체나 폴백이 한 군데서 끝나야 한다(get_llm 하나만 본다).
    """
    chain = SUMMARY_PROMPT | get_llm() | StrOutputParser()
    config = {"callbacks": [tracker]} if tracker else {}
    async for piece in chain.astream({"content": content}, config=config):
        if piece:
            yield piece
