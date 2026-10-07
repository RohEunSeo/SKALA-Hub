"""평가셋 재료 수집: 슬랙에 실제로 올라온 질문 문장을 뽑는다.

왜 필요한가
-----------
평가셋 질문을 **게시글 목록을 보고** 만들면 "답이 있는 질문"만 나온다(테스트셋 누출).
실제 사용자가 쓴 문장에서 출발해야 말투·오타·줄임말까지 실제 분포를 따라간다.

출력은 **화면으로만** 보낸다. 질문 문장에 실명이 섞일 수 있고 이 저장소는 public이다.
(멘션 <@U123|이름>은 loader.clean_slack_text가 지우지만 본문에 적힌 이름은 못 지운다)

사용: uv run python -m scripts.harvest_questions
"""
import re
import sys

from app.core.loader import BOT_NAME, clean_slack_text
from app.db import fetch_all

MIN_CHARS = 6
MAX_CHARS = 160

# 질문 신호. 물음표 없이 끝나는 한국어 질문이 많아 어미·의문사·요청표현을 같이 본다.
_ENDING = re.compile(r"(나요|까요|ㄴ가요|인가요|은가요|는지|거죠|죠\?|어요\?|에요\?|예요\?|니까)\s*[?!.]*$")
_WH = re.compile(r"(어디|어떻게|어떤|무슨|뭐|무엇|언제|왜|누가|얼마|몇)")
_ASK = re.compile(r"(추천|알려주|공유\s*(해|부)|아시는\s*분|있으신\s*분|궁금|문의|질문\s*있)")

# 뽑은 질문을 사람이 훑기 쉽게 거칠게 묶는다 (정확한 분류가 목적이 아니다)
_FIND = re.compile(r"(자료|링크|사이트|강의|영상|책|교재|깃허브|github|레포|문서|자격증|추천|어디)")
_HOW = re.compile(r"(어떻게|방법|하는\s*법|되나요|설치|설정|오류|에러|안\s*되)")

# 스레드 운영용 문장. 챗봇에 물을 질문이 아니다
_LOGISTICS = re.compile(r"(참석|참여\s*(가능|희망)|신청|출석|지각|자리\s*있|오시|가시|시간\s*괜찮|일정\s*괜찮)")

_SPLIT = re.compile(r"(?<=[?!])\s+|\n+")


def question_sentences(text: str) -> list[str]:
    """한 덩어리 글에서 질문으로 보이는 문장만 골라낸다."""
    out = []
    for raw in _SPLIT.split(text):
        s = raw.strip(" \t-·•")
        if not (MIN_CHARS <= len(s) <= MAX_CHARS):
            continue
        if not (s.endswith("?") or _ENDING.search(s) or (_WH.search(s) and _ASK.search(s))):
            continue
        out.append(s)
    return out


def bucket(s: str) -> str:
    if _LOGISTICS.search(s):
        return "운영"       # 챗봇 질문 아님 - 참고용으로만 센다
    if _FIND.search(s):
        return "자료찾기"   # ★ 챗봇이 답해야 하는 핵심 유형
    if _HOW.search(s):
        return "방법"
    return "기타"


REPLIES_SQL = """
SELECT r.post_id, r.content, p.ai_title
FROM replies r
JOIN posts p ON p.id = r.post_id
WHERE r.user_name <> %(bot)s
  AND COALESCE(r.content, '') <> ''
ORDER BY r.post_id, r.created_at
"""

POSTS_SQL = """
SELECT id, ai_title, content, category
FROM posts
WHERE is_deleted = false AND COALESCE(content, '') <> ''
ORDER BY id
"""


def main() -> None:
    buckets: dict[str, list[str]] = {}

    def add(b: str, line: str) -> None:
        buckets.setdefault(b, []).append(line)

    reply_rows = fetch_all(REPLIES_SQL, {"bot": BOT_NAME})
    n_reply_q = 0
    for row in reply_rows:
        title = (row.get("ai_title") or "").strip()[:28]
        for s in question_sentences(clean_slack_text(row["content"])):
            n_reply_q += 1
            add(bucket(s), f"  [댓글 post {row['post_id']:>3}] {s}\n{'':>22}↳ 글: {title}")

    post_rows = fetch_all(POSTS_SQL)
    n_post_q = 0
    for row in post_rows:
        for s in question_sentences(clean_slack_text(row["content"])):
            n_post_q += 1
            add(bucket(s), f"  [게시글 {row['id']:>3}] {s}")

    print(f"댓글 {len(reply_rows)}개 → 질문 문장 {n_reply_q}개")
    print(f"게시글 {len(post_rows)}개 → 질문 문장 {n_post_q}개\n")

    for b in ("자료찾기", "방법", "기타", "운영"):
        items = buckets.get(b, [])
        mark = " ★ 평가셋 후보" if b == "자료찾기" else ""
        print(f"\n{'=' * 70}\n{b}  {len(items)}개{mark}\n{'=' * 70}")
        for line in items:
            print(line)


if __name__ == "__main__":
    sys.exit(main())
