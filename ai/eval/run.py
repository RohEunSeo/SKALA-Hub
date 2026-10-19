"""평가셋을 돌려 베이스라인 수치를 낸다.

    uv run python -m eval.run              # 검색만 (임베딩 비용뿐, ~0.03원)
    uv run python -m eval.run --gen        # 생성까지 (LLM 25회 — 하루 한도 주의)
    uv run python -m eval.run --k 10       # Top-K 변경
    uv run python -m eval.run --save baseline.json

왜 검색과 생성을 나눠 돌리는가
    종합실습에서 Q5가 실패했을 때 원인을 Retrieval로 오진해 MMR·ParentDocument를
    적용했고 둘 다 악화됐다. 정답 문서가 Context에 있는데도 모델이 거절한 것,
    즉 Generation 문제였다. 두 단계를 따로 재야 그 구분이 된다.

지표
    Hit@K   정답 글(또는 정답 키워드)이 Top-K 안에 있나        ← Retrieval
    MRR@K   정답이 몇 번째로 나왔나 (1위=1.0, 3위=0.33)      ← Retrieval
    Route   라우터가 기대한 경로로 보냈나                      ← 라우팅
    Abstain 답이 없어야 하는 질문을 제대로 거절했나             ← Generation
"""
import argparse
import asyncio
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from app.config import settings
from app.core.generator import is_abstention
from app.core.grader import agrade
from app.core.router import route as route_of
from app.db import fetch_all
from app.services.rag_service import search, stream_answer

QUESTIONS = Path(__file__).parent / "questions.jsonl"


def load_questions() -> list[dict]:
    return [json.loads(l) for l in QUESTIONS.read_text(encoding="utf-8").splitlines() if l.strip()]


def hit_and_rank(docs, item) -> tuple[int, float]:
    """정답이 Top-K에 있나(hit), 몇 번째인가(reciprocal rank).

    판정 기준이 둘이다.
      gold_doc_ids  - 글 번호가 맞는지. 정확하지만 라벨링이 번거롭다
      gold_keywords - 그 글에 반드시 있는 단어가 검색 결과에 있는지.
                      종합실습에서 쓴 방식으로, 라벨링이 훨씬 빠르다
    둘 다 있으면 글 번호를 우선한다(더 엄격).
    """
    ids = item.get("gold_doc_ids") or []
    kws = item.get("gold_keywords") or []

    if ids:
        want = set(ids)
        seen, rank = [], 0.0
        for i, d in enumerate(docs, start=1):
            pid = d.metadata.get("post_id")
            if pid in want and pid not in seen:
                seen.append(pid)
                if not rank:
                    rank = 1.0 / i
        # policy=all 이면 정답 글이 **전부** 나와야 Hit
        need_all = item.get("gold_doc_policy") == "all"
        ok = len(seen) == len(want) if need_all else bool(seen)
        return (1 if ok else 0), (rank if ok else 0.0)

    if kws:
        joined = "\n".join(d.page_content for d in docs)
        for i, d in enumerate(docs, start=1):
            if any(k.lower() in d.page_content.lower() for k in kws):
                return 1, 1.0 / i
        return (1 if any(k.lower() in joined.lower() for k in kws) else 0), 0.0

    return 0, 0.0  # 정답이 안 적힌 문항은 Hit 계산에서 빠진다


async def run_one(item: dict, k: int, gen: bool) -> dict:
    q = item["question"]
    got_route = route_of(q)
    r = {
        "id": item["id"], "question": q, "category": item["category"],
        "want_route": item.get("route"), "got_route": got_route,
        "route_ok": item.get("route") == got_route,
        "answerable": item.get("answerable", True),
        "hit": None, "rr": None, "abstained": None, "top1": None,
    }

    # 검색을 쓰지 않는 경로(about/how/popular/reject/clarify)는 Hit을 재지 않는다
    if got_route in ("search", "count", "similar"):
        docs = await search(q, k=k)
        r["top1"] = round(float(docs[0].metadata["score"]), 3) if docs else None
        # retrieved 는 **거르기 전** 검색 결과다 - 지난 결과 파일과 같은 뜻이어야 비교가 된다.
        r["retrieved"] = [d.metadata.get("post_id") for d in docs]
        # grade 가 켜져 있으면 무관한 글을 버린다. Hit/RR 은 **남은 글**로 잰다
        # - "쓰레기를 버리려다 정답을 버렸나"를 보는 게 이 측정의 핵심이다.
        docs = await agrade(q, docs)
        r["kept"] = [d.metadata.get("post_id") for d in docs]
        # Hit@K 는 '검색이 정답 글을 찾았나'만 재야 한다.
        # meta(넌 누구야) · impossible(집계) 은 애초에 글을 찾는 질문이 아니므로 분모에서 뺀다.
        # 안 그러면 라우팅 실패가 검색 실패로 이중 계상돼 두 지표가 같은 것을 두 번 센다.
        if item.get("answerable", True) and item["category"] not in ("meta", "impossible"):
            r["hit"], r["rr"] = hit_and_rank(docs, item)
        if gen:
            text = "".join([p async for p in stream_answer(q, docs)])
            r["abstained"] = is_abstention(text)
            r["answer"] = text[:200]
    return r


async def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--k", type=int, default=5)
    p.add_argument("--gen", action="store_true", help="생성까지 평가 (LLM 호출)")
    p.add_argument("--no-grade", action="store_true",
                   help="관련성 판정을 끈다 (grade 전/후 비교용). 기본은 켜짐")
    p.add_argument("--save", help="결과를 JSON으로 저장")
    a = p.parse_args()

    # 평가에서는 .env 값보다 플래그가 이긴다. 켜고/끄고 한 번씩 돌려 비교하는 게 목적이다.
    settings.grade_enabled = not a.no_grade

    items = load_questions()
    # 데이터가 매일 늘어나므로 언제·무엇으로 잰 수치인지 같이 남긴다.
    # 이게 없으면 0.80 과 0.75 를 비교할 수 없다 (개선 탓인지 데이터 탓인지).
    idx = fetch_all("select count(*) c, count(distinct post_id) p from post_chunks")[0]
    env = {
        "at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "k": a.k, "chunks": idx["c"], "posts": idx["p"],
        "store": settings.vector_store, "index_version": settings.index_version,
        "embedding": settings.embedding_model,
        "llm": settings.llm_model if a.gen else None,
        "grade": settings.grade_enabled,
    }
    print(f"  {env['posts']}글 / {env['chunks']}조각 · k={a.k} · {env['embedding']}"
          + (f" · {env['llm']}" if a.gen else " · 검색만")
          + (" · grade ON" if env["grade"] else " · grade OFF"))
    print()

    results = [await run_one(it, a.k, a.gen) for it in items]

    print(f"  {'ID':<5} {'유형':<13} {'경로':<9} {'Hit':<4} {'RR':<5} {'1위거리':<7} 질문")
    print("  " + "-" * 92)
    for r in results:
        rt = f"{r['got_route']}" + ("" if r["route_ok"] else f"✗({r['want_route']})")
        hit = "-" if r["hit"] is None else ("O" if r["hit"] else "X")
        rr = "  -  " if r["rr"] is None else f"{r['rr']:.2f} "
        d1 = f"{r['top1']:.3f}" if r["top1"] is not None else "  -  "
        print(f"  {r['id']:<5} {r['category']:<13} {rt:<9} {hit:<4} {rr:<5} {d1:<7} {r['question'][:38]}")

    scored = [r for r in results if r["hit"] is not None]
    hits = sum(r["hit"] for r in scored)
    mrr = sum(r["rr"] for r in scored) / len(scored) if scored else 0.0
    route_ok = sum(1 for r in results if r["route_ok"])

    # Precision - "5개 중 몇 개가 쓸만한가". Hit@K가 **구조적으로 못 보는** 값이다
    # (정답이 하나라도 들어 있으면 나머지 4개가 쓰레기여도 Hit은 만점).
    # 주의: gold_doc_ids 는 "관련 글 전부"가 아니라 "확실히 관련 있는 글 몇 개"라서
    # 이 값은 **하한**이다. 절대값으로 읽지 말고 전/후 비교에만 쓴다.
    golds = {it["id"]: set(it["gold_doc_ids"]) for it in items if it.get("gold_doc_ids")}
    precs, kepts = [], []
    for r in results:
        shown = r.get("kept", r.get("retrieved"))
        if shown is None:
            continue
        kepts.append(len(shown))
        g = golds.get(r["id"])
        if g and shown:
            precs.append(len(set(shown) & g) / len(shown))

    print("\n  === 베이스라인 ===")
    print(f"    Hit@{a.k}   {hits}/{len(scored)} = {hits/len(scored):.3f}" if scored else "    Hit: 측정 대상 없음")
    print(f"    MRR@{a.k}   {mrr:.3f}")
    if precs:
        print(f"    Prec@{a.k}  {sum(precs)/len(precs):.3f}   ({len(precs)}문항, gold 기준 하한)")
    if kepts:
        print(f"    남은 글   평균 {sum(kepts)/len(kepts):.2f}개 / {a.k}개")
    print(f"    Route    {route_ok}/{len(results)} = {route_ok/len(results):.3f}")

    if a.gen:
        unans = [r for r in results if not r["answerable"] and r["abstained"] is not None]
        if unans:
            ok = sum(1 for r in unans if r["abstained"])
            print(f"    거절 정확도 {ok}/{len(unans)} = {ok/len(unans):.3f}  (답이 없어야 하는 질문)")
        ans = [r for r in results if r["answerable"] and r["abstained"] is not None]
        if ans:
            wrong = sum(1 for r in ans if r["abstained"])
            print(f"    과잉 거절  {wrong}/{len(ans)}  (답이 있는데 못 찾았다고 한 것)")

    bad = [r for r in results if not r["route_ok"]]
    if bad:
        print("\n  === 라우팅 틀린 문항 ===")
        for r in bad:
            print(f"    {r['id']}  {r['want_route']} → {r['got_route']}   {r['question'][:46]}")

    if a.save:
        Path(a.save).write_text(json.dumps({"env": env, "results": results}, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n  저장: {a.save}")


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
