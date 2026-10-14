"""chat_logs를 사람이 읽을 수 있는 리포트로 뽑는다.

왜 필요한가 - 실사용 로그가 평가셋의 유일한 재료이기 때문이다.
슬랙 채널엔 정보탐색 질문이 거의 없어서(사람 댓글 281개 중 2개), 진짜 질문은
챗봇을 열어서 받는 수밖에 없다. 그 질문을 눈으로 읽어야 다음에 뭘 고칠지 정해진다.

**관리자(본인) 테스트는 뺀다.** 내가 던진 질문은 내가 아는 답이 있는 질문이라
실제 사용자가 무엇을 기대하는지는 알려주지 않는다.

교육생 실명이 들어가므로 출력은 U01, U02로 익명화한다 (이 저장소는 public).

    uv run --no-sync python -m scripts.export_logs            # 리포트
    uv run --no-sync python -m scripts.export_logs --csv      # 원본 CSV도 같이
"""
import csv
import sys
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

from app.db import fetch_all

# docs/ 는 gitignore라 실명이 섞인 원본을 둬도 안전하다.
OUT_DIR = Path(__file__).resolve().parents[2] / "docs" / "ai-chatbot"

SQL = """
SELECT c.id, c.created_at, c.user_slack_id, u.name, u.role,
       c.question, c.route, c.category,
       c.retrieved_post_ids, c.retrieved_scores,
       c.answer, c.abstained, c.llm_called, c.model, c.latency_ms,
       c.prompt_tokens, c.completion_tokens, c.error,
       c.feedback, c.feedback_reason
FROM chat_logs c
LEFT JOIN users u ON u.slack_id = c.user_slack_id
ORDER BY c.created_at
"""

FACE = {1: "👍", 0: "😐", -1: "👎"}


def anonymize(rows: list[dict]) -> dict[str, str]:
    """먼저 질문한 사람부터 U01. 관리자는 ADMIN으로 따로 표시한다."""
    alias, n = {}, 0
    for r in rows:
        sid = r["user_slack_id"]
        if sid in alias:
            continue
        if r["role"] == "admin":
            alias[sid] = "ADMIN"
        else:
            n += 1
            alias[sid] = f"U{n:02d}"
    return alias


def pct(a: int, b: int) -> str:
    return f"{a}/{b} = {a / b:.1%}" if b else f"{a}/0"


def quantile(xs: list[float], q: float) -> float:
    """numpy 없이. xs는 정렬된 상태로 받는다."""
    if not xs:
        return 0.0
    i = min(int(round(q * (len(xs) - 1))), len(xs) - 1)
    return xs[i]


def main() -> None:
    rows = fetch_all(SQL)
    alias = anonymize(rows)
    real = [r for r in rows if r["role"] != "admin"]
    llm = [r for r in real if r["llm_called"]]

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    today = date.today().isoformat()
    out = OUT_DIR / f"user-logs-{today}.md"

    L: list[str] = []
    w = L.append

    w(f"# 챗봇 실사용 로그 — {today}")
    w("")
    w("> `ai/scripts/export_logs.py` 가 생성. **관리자(본인) 테스트는 제외**했습니다.")
    w("> 교육생 실명은 U01~로 익명화. `docs/`는 gitignore라 로컬 전용입니다.")
    w("")

    # ---- 요약 -----------------------------------------------------------
    days = {r["created_at"].date() for r in real}
    w("## 1. 한눈에")
    w("")
    w("| 항목 | 값 |")
    w("|---|---|")
    w(f"| 기간 | {min(days)} ~ {max(days)} ({len(days)}일) |")
    w(f"| 실사용자 | {len({r['user_slack_id'] for r in real})}명 |")
    w(f"| 질문 | {len(real)}건 (LLM 호출 {len(llm)}건) |")
    w(f"| 관리자 테스트(제외분) | {len(rows) - len(real)}건 |")
    ab = [r for r in llm if r["abstained"]]
    w(f"| **거절(\"찾지 못했어요\")** | **{pct(len(ab), len(llm))}** |")
    err = [r for r in real if r["error"]]
    w(f"| 에러 | {pct(len(err), len(real))} |")
    fb = [r for r in real if r["feedback"] is not None]
    w(f"| 피드백 | {pct(len(fb), len(real))} |")
    lat = sorted(r["latency_ms"] for r in llm if r["latency_ms"])
    if lat:
        w(f"| 응답시간 p50 / p95 / 최대 | {quantile(lat, .5):,.0f} / "
          f"{quantile(lat, .95):,.0f} / {max(lat):,.0f} ms |")
    tok = sum(1 for r in llm if r["prompt_tokens"])
    w(f"| 토큰 기록된 건 | **{pct(tok, len(llm))}** ← 비용 측정 불가 |")
    w("")

    # ---- 사용자별 --------------------------------------------------------
    w("## 2. 사용자별")
    w("")
    w("| 사용자 | 질문 | 거절 | 피드백 | 기간 |")
    w("|---|---:|---:|---:|---|")
    by_user: dict[str, list[dict]] = defaultdict(list)
    for r in real:
        by_user[alias[r["user_slack_id"]]].append(r)
    for u, rs in sorted(by_user.items(), key=lambda kv: -len(kv[1])):
        d = sorted({r["created_at"].date() for r in rs})
        nab = sum(1 for r in rs if r["abstained"])
        nfb = sum(1 for r in rs if r["feedback"] is not None)
        span = f"{d[0]}" if len(d) == 1 else f"{d[0]}~{d[-1]}"
        w(f"| {u} | {len(rs)} | {nab} | {nfb} | {span} |")
    w("")

    # ---- 라우트 ----------------------------------------------------------
    w("## 3. 라우트 분포")
    w("")
    w("| route | 건수 | 거절 |")
    w("|---|---:|---:|")
    cnt = Counter(r["route"] for r in real)
    abr = Counter(r["route"] for r in real if r["abstained"])
    for k, n in cnt.most_common():
        w(f"| `{k}` | {n} | {abr.get(k, 0)} |")
    w("")

    # ---- 재질문 ----------------------------------------------------------
    w("## 4. 같은 질문 반복 (재질문 = 첫 답이 실패했다는 신호)")
    w("")
    rep = Counter((alias[r["user_slack_id"]], r["question"].strip()) for r in real)
    rep = {k: v for k, v in rep.items() if v > 1}
    if rep:
        w("| 횟수 | 사용자 | 질문 |")
        w("|---:|---|---|")
        for (u, q), n in sorted(rep.items(), key=lambda kv: -kv[1]):
            w(f"| {n} | {u} | {q[:70]} |")
    else:
        w("없음")
    w("")

    # ---- 거절 전체 -------------------------------------------------------
    w("## 5. 거절된 질문 전체")
    w("")
    w("`d1` = 1위 문서와의 L2 거리 (작을수록 가까움, 1.414가 '전혀 무관')")
    w("")
    w("| # | d1 | 질문 |")
    w("|---|---:|---|")
    for r in ab:
        s = (r["retrieved_scores"] or [0])[0]
        w(f"| {r['id']} | {s:.3f} | {r['question'].strip()[:80]} |")
    w("")

    # ---- 거리 분포 -------------------------------------------------------
    ok_d = sorted((r["retrieved_scores"] or [0])[0] for r in llm if not r["abstained"]
                  and r["retrieved_scores"])
    ab_d = sorted((r["retrieved_scores"] or [0])[0] for r in ab if r["retrieved_scores"])
    if ok_d and ab_d:
        lo, hi = min(ab_d), max(ok_d)
        w("## 6. 유사도 거리로 거절을 가를 수 있나 — 없다")
        w("")
        w("| | n | 최소 | 중앙 | 최대 |")
        w("|---|---:|---:|---:|---:|")
        w(f"| 답변함 | {len(ok_d)} | {min(ok_d):.3f} | {quantile(ok_d, .5):.3f} | {max(ok_d):.3f} |")
        w(f"| 거절함 | {len(ab_d)} | {min(ab_d):.3f} | {quantile(ab_d, .5):.3f} | {max(ab_d):.3f} |")
        w("")
        o1 = [x for x in ok_d if x >= lo]
        o2 = [x for x in ab_d if x <= hi]
        w(f"겹치는 구간 **{lo:.3f} ~ {hi:.3f}** 안에 "
          f"답변한 것의 {len(o1) / len(ok_d):.0%}, 거절한 것의 {len(o2) / len(ab_d):.0%}가 들어 있다.")
        w("→ **거리 임계값 하나로는 못 가른다.** (오프라인 평가에서 내린 결론이 실사용에서도 재현됨)")
        w("")

    # ---- 피드백 ----------------------------------------------------------
    w("## 7. 피드백 원문")
    w("")
    for r in fb:
        w(f"**{FACE.get(r['feedback'], '?')} #{r['id']}** · {alias[r['user_slack_id']]} · "
          f"거절={'Y' if r['abstained'] else 'N'}")
        w("")
        w(f"- 질문: {r['question'].strip()}")
        w(f"- 이유: {r['feedback_reason'] or '(없음)'}")
        w(f"- 답변: {(r['answer'] or '').strip()[:300]}")
        w("")

    # ---- 전문 ------------------------------------------------------------
    w("## 8. 질문·답변 전문 (시간순)")
    w("")
    for r in real:
        f = FACE.get(r["feedback"], "")
        flags = []
        if r["abstained"]:
            flags.append("거절")
        if r["error"]:
            flags.append("에러")
        if not r["llm_called"]:
            flags.append("고정문구")
        tag = f" `{'·'.join(flags)}`" if flags else ""
        w(f"### #{r['id']} · {alias[r['user_slack_id']]} · "
          f"{r['created_at']:%m-%d %H:%M} · `{r['route']}` · "
          f"{r['latency_ms'] or 0:,}ms{tag} {f}")
        w("")
        w(f"> {r['question'].strip()}")
        w("")
        if r["error"]:
            w(f"- ⚠️ 에러: `{str(r['error'])[:200]}`")
        w(f"- 답변: {(r['answer'] or '(없음)').strip()[:500]}")
        if r["feedback"] is not None:
            w(f"- 피드백: {FACE.get(r['feedback'])} {r['feedback_reason'] or ''}")
        w("")

    out.write_text("\n".join(L), encoding="utf-8")
    print(f"리포트: {out}  ({out.stat().st_size:,} bytes)")

    if "--csv" in sys.argv:
        csv_path = OUT_DIR / f"user-logs-{today}.csv"
        cols = ["id", "created_at", "user", "question", "route", "category",
                "abstained", "llm_called", "latency_ms", "feedback",
                "feedback_reason", "error", "answer"]
        with csv_path.open("w", newline="", encoding="utf-8-sig") as fp:
            wr = csv.DictWriter(fp, fieldnames=cols, extrasaction="ignore")
            wr.writeheader()
            for r in real:
                wr.writerow({**r, "user": alias[r["user_slack_id"]]})
        print(f"CSV:    {csv_path}")


if __name__ == "__main__":
    main()
