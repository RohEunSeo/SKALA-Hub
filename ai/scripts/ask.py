"""터미널에서 질문해 본다. (Step 5 — Baseline 챗봇)

    uv run python -m scripts.ask "SQLD 자료 추천해줘"
    uv run python -m scripts.ask                      # 확인용 질문 묶음
"""
import sys
import time

from app.config import settings
from app.services.rag_service import answer

# 일부러 성격이 다른 것들을 섞었다. 마지막 둘은 "모른다고 해야 하는" 질문이다.
DEFAULT_QUESTIONS = [
    "SQLD 자료 추천해줘",
    "맥에서 쓸만한 툴 있어?",
    "판교 근처 맛집 알려줘",
    "RAG 공부할 만한 자료 있어?",
    "내일 서울 날씨 어때?",          # 서비스 밖 - 거절해야 함
    "SKALA 수료식이 언제야?",        # 글에 없을 가능성 - 모른다고 해야 함
]


def main() -> None:
    questions = sys.argv[1:] or DEFAULT_QUESTIONS
    print(f"모델 {settings.llm_model} / 근거 글 {settings.vector_store} 인덱스\n")

    for q in questions:
        print("=" * 78)
        print(f"Q. {q}")
        started = time.time()
        try:
            out = answer(q)
        except Exception as e:
            print(f"   실패: {type(e).__name__}: {e}")
            continue
        print(f"\nA. {out['answer']}")
        print(f"\n   근거로 쓴 글 {len(out['sources'])}개 ({time.time() - started:.1f}초)")
        for s in out["sources"]:
            print(f"     post {s['id']:>3} · {s['category']} · 👍{s['reactions']} · {s['title'][:48]}")
        print()


if __name__ == "__main__":
    main()
