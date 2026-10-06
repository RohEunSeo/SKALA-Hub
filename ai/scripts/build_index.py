"""게시글 전체를 숫자로 바꿔 인덱스를 만든다. (돈이 드는 유일한 작업)

    uv run python -m scripts.build_index --dry-run   # 비용만 계산, 호출 안 함
    uv run python -m scripts.build_index             # 실제로 만들기

한 번만 돌린다. 질문할 때마다 임베딩하면 비용이 터진다.
새 글이 올라와 다시 반영하고 싶을 때만 또 돌리면 된다.
"""
import sys
import time

from app.config import settings
from app.core.loader import load_chunks
from app.core.retriever import build_index

# text-embedding-3-small 단가 (100만 토큰당 USD). 바뀌면 여기만 고친다.
USD_PER_1M_TOKENS = 0.02
USD_TO_KRW = 1400
# 한국어는 글자당 토큰이 영어보다 많다. 넉넉하게 1.3배로 잡아 비용을 과소평가하지 않는다.
TOKENS_PER_CHAR = 1.3


def main() -> None:
    dry = "--dry-run" in sys.argv

    print("게시글을 불러오는 중…")
    chunks = load_chunks()
    chars = sum(len(c.page_content) for c in chunks)
    tokens = int(chars * TOKENS_PER_CHAR)
    krw = tokens / 1_000_000 * USD_PER_1M_TOKENS * USD_TO_KRW

    posts = len({c.metadata["post_id"] for c in chunks})
    print(f"  글 {posts}개 → 조각 {len(chunks)}개 / 글자 {chars:,}자")
    print(f"  예상 토큰 약 {tokens:,}개 → 예상 비용 약 {krw:.1f}원")
    print(f"  모델 {settings.embedding_model} / 저장소 {settings.vector_store}")

    if dry:
        print("\n--dry-run 이므로 여기서 멈춥니다 (돈이 나가지 않았습니다).")
        return

    print(f"\n숫자로 바꾸는 중… (1~2분)")
    started = time.time()
    saved = build_index(chunks)
    took = time.time() - started

    if settings.vector_store == "pgvector":
        # 넣은 뒤 실제로 몇 행이 들어갔는지 DB에 되물어 확인한다.
        # "넣었다고 했는데 0행"이면 바로 알아야 한다
        from app.db import fetch_all
        n = fetch_all("select count(*) as n from post_chunks")[0]["n"]
        print(f"\n완료 ({took:.0f}초) → Supabase post_chunks 테이블")
        print(f"  저장 요청 {saved}행 / 실제 {n}행 {'✅' if n == saved else '⚠️ 다릅니다'}")
        print("\n다음: .env 의 VECTOR_STORE=pgvector 로 바꾸고 검색을 비교해 보세요")
        return

    files = sorted(settings.faiss_dir.glob("*"))
    total = sum(f.stat().st_size for f in files)
    print(f"\n완료 ({took:.0f}초) → {settings.faiss_dir}")
    for f in files:
        print(f"  {f.name:<14} {f.stat().st_size / 1024:>8,.0f} KB")
    print(f"  {'합계':<14} {total / 1024:>8,.0f} KB")
    print("\n다음: 검색이 제대로 되는지 확인 (Step 4)")


if __name__ == "__main__":
    main()
