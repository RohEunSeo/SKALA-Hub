"""검색만 해본다. LLM은 쓰지 않는다. (Step 4 — 제일 중요한 확인 지점)

    uv run python -m scripts.try_search "SQLD"
    uv run python -m scripts.try_search "맥에서 쓸만한 툴" -k 8
    uv run python -m scripts.try_search            # 기본 질문 묶음을 한 번에

여기서 엉뚱한 글이 나오면 뒤를 아무리 잘 만들어도 소용없다.
LLM은 검색해 온 글 안에서만 답하기 때문이다.
의심할 순서: 1) loader의 자르기 기준  2) k 값  3) 임베딩 모델
"""
import sys
from collections import Counter

from app.core.retriever import load_index

# 실제로 올라온 글을 생각하며 고른 확인용 질문들.
# 일부러 "본문에 그 단어가 없는" 질문을 섞었다 - 단어가 겹쳐서 찾는 게 아니라
# 뜻이 비슷해서 찾는지 봐야 하기 때문이다.
DEFAULT_QUERIES = [
    "SQLD",                      # 단어가 그대로 있는 글이 있음
    "자격증 준비 어떻게 해?",      # '자격증'이란 단어 없이도 SQLD 글이 나와야 함
    "맥에서 쓸만한 툴",
    "면접 후기",
    "점심 맛집",
]


def show(query: str, k: int) -> None:
    store = load_index()
    hits = store.similarity_search_with_score(query, k=k)

    print(f"\n{'=' * 78}")
    print(f"질문: {query}")
    print("=" * 78)

    if not hits:
        print("  (아무것도 안 나왔습니다)")
        return

    seen = Counter(d.metadata["post_id"] for d, _ in hits)
    for rank, (doc, dist) in enumerate(hits, 1):
        m = doc.metadata
        dup = "  ← 같은 글 조각 중복" if seen[m["post_id"]] > 1 else ""
        title = m["ai_title"] or doc.page_content.split("\n")[0]
        cos = 1 - dist * dist / 2  # 정규화 벡터에서 L2거리 → 코사인 유사도
        print(f"\n{rank}. [거리 {dist:.3f} / 유사도 {cos:+.2f}] post {m['post_id']} "
              f"({m['chunk_index'] + 1}/{m['chunk_total']}){dup}")
        print(f"   {title[:62]}")
        print(f"   {m['category']} {m['tags']} 👍{m['reaction_count']} 💬{m['reply_count']}"
              + (f" · 커리큘럼 {m['stage']}/{m['sub_category']}" if m["stage"] else ""))
        body = " ".join(doc.page_content.split())
        print(f"   {body[:110]}…")

    dups = sum(1 for n in seen.values() if n > 1)
    if dups:
        print(f"\n  ※ 서로 다른 글 {len(seen)}개뿐 ({k}개 중). "
              f"같은 글 조각이 자리를 나눠 가졌다 → chunk_overlap 재검토 대상")


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    k = 5
    if "-k" in sys.argv:
        k = int(sys.argv[sys.argv.index("-k") + 1])

    queries = args or DEFAULT_QUERIES
    print(f"조각 {load_index().index.ntotal}개에서 검색 (상위 {k}개씩)")
    for q in queries:
        show(q, k)
    print(f"\n{'=' * 78}")
    print("볼 것: 1) 나온 글이 질문과 말이 되는가  2) 같은 글 조각이 중복으로 떴는가")
    print("거리 눈금 (FAISS 기본은 코사인이 아니라 L2 거리다 - 정규화 벡터 기준):")
    print("  0.00 = 완전 동일 | 0.95 ≈ 유사도 0.55 | 1.23 ≈ 0.24 | 1.414 = 전혀 무관")
    print("  환산식: 코사인 = 1 - 거리² / 2")


if __name__ == "__main__":
    main()
