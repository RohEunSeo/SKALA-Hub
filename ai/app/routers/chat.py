"""POST /chat — 질문을 받아 답변을 조금씩 흘려보낸다.

흐름은 목업이 정한 순서를 그대로 따른다 (api/chat.js:75~83).

    status(search_posts) → status(compose) → token… → sources → usage → done

화면 쪽에서 왜 이 순서가 중요한가:
- search_posts 가 켜지면 피드에 "검색 중" 모션이 돈다 (FeedView.vue:145)
- sources 가 와야 추천 글이 피드 위로 올라온다

1차 베타(판교 5반)에서 **일부러 뺀 것**
- save_proposal: 폴더가 localStorage라 기기를 바꾸면 사라진다. 저장했다고 믿고
  잃어버리는 쪽이 검색 품질보다 더 큰 신뢰 손해다. 서버가 이벤트를 안 보내면
  화면은 아무것도 그리지 않으므로 프론트에 분기를 넣지 않는다.
"""
import logging
import re
import time
from collections.abc import AsyncIterator
from typing import Any

import jwt
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import settings
from app.core.generator import ABSTAIN_PHRASE
from app.core.router import route
from app.schemas import chat as ev
from app.services import log_service
from app.services.rag_service import _to_sources, search, stream_answer
from app.services.stats_service import popular_posts

log = logging.getLogger(__name__)
router = APIRouter()

# Spring의 JwtService가 Keys.hmacShaKeyFor(secret.getBytes(UTF_8)) 를 쓴다.
# 시크릿 길이에 따라 HS256/384/512 중 하나가 자동으로 선택되므로 셋 다 받아준다.
JWT_ALGORITHMS = ["HS256", "HS384", "HS512"]

# 고정 문구들 - LLM을 부르지 않는다. 답이 항상 같은데 매번 물어볼 이유가 없다 (0원, 즉시).
ABOUT_TEXT = (
    "저는 SKALA Hub에 올라온 글을 찾아드리는 도우미예요.\n\n"
    "· 궁금한 주제로 물어보면 관련 글을 찾아 요약해 드려요\n"
    "· 찾은 글은 피드 맨 위로 올려서 바로 확인할 수 있어요\n"
    "· 반응이 많았던 글도 알려드려요\n\n"
    "예를 들어 \"맥에서 쓸만한 툴 있어?\" 처럼 물어봐 주세요."
)

# 동작 방식 설명. "추천 기준이 뭐야?" 류에 쓴다.
# 솔직하게 적는다 - 한계를 숨기면 왜 엉뚱한 글이 나오는지 사용자가 영영 모른다.
HOW_TEXT = (
    "글의 뜻을 숫자로 바꿔서, 질문과 가장 비슷한 글을 찾아요.\n\n"
    "· 단어가 똑같지 않아도 뜻이 비슷하면 찾아요\n"
    "  (\"자격증 준비\"로 물어도 SQLD 글이 나와요)\n"
    "· 지금 보고 있는 탭 안에서 먼저 찾고, 없으면 전체로 넓혀요\n"
    "· 한 번에 5개까지 보여드려요\n\n"
    "아직 못 하는 것도 있어요 — 반응 수나 최신순 같은 건 따로 세어서 알려드리고, "
    "\"가장 많이 언급된 주제\" 같은 건 준비 중이에요."
)

REJECT_TEXT = (
    "저는 SKALA Hub에 올라온 글을 찾는 것만 도와드릴 수 있어요.\n"
    "대신 이런 건 물어보실 수 있어요 — \"SQLD 자료 추천해줘\", \"이번 달 인기 글 보여줘\""
)

# 인젝션 차단. 훈계하거나 "그런 시도는 안 통해요" 같은 말을 하지 않는다
# - 공격자에게는 힌트가 되고, 장난으로 쳐본 사람에게는 무안하다. 담백하게 거절하고 본론으로 돌린다.
BLOCK_TEXT = (
    "해당 요청은 들어드릴 수 없어요.\n"
    "저는 SKALA Hub에 올라온 글을 찾아드리는 일만 해요. 찾고 싶은 주제를 말씀해 주세요."
)

# 사람 추적 거절. 이건 공격이 아니라 **정책 질문**이라 이유와 대안을 같이 준다
# (홈 순위보드도 글은 줄 세우지만 사람은 줄 세우지 않는다 - 챗봇만 그 선을 넘을 수 없다)
PERSON_TEXT = (
    "사람별 활동 순위는 알려드리지 않아요.\n"
    "글 개수가 성실도처럼 읽힐 수 있어서예요.\n\n"
    "대신 반응이 많았던 글은 보여드릴 수 있어요 — \"이번 달 인기 글 보여줘\" 라고 물어봐 주세요."
)

# 베타 공개 범위 밖. 프론트에서 버튼을 숨기지만 그건 우회가 가능하므로 서버도 막는다.
CLOSED_TEXT = (
    "아직 준비 중이에요. 일부 반에서 먼저 테스트하고 있어요.\n"
    "곧 모두에게 열어드릴게요!"
)

LIMIT_TEXT = (
    "오늘 질문을 다 쓰셨어요. 내일 다시 물어봐 주세요!\n"
    "(아직 테스트 중이라 하루 사용량을 제한하고 있어요)"
)

NOT_FOUND_TEXT = "관련된 글을 찾지 못했어요. 다른 말로 물어봐 주시겠어요?"

# 되묻기 선택지. value는 다음 턴에 그대로 다시 물어볼 문장이다.
CLARIFY_OPTIONS = [
    {"label": "🛠️ 개발 툴·환경", "value": "개발 툴·환경 관련 글 추천해줘"},
    {"label": "📚 학습자료", "value": "학습자료 관련 글 추천해줘"},
    {"label": "🏆 자격증·취업", "value": "자격증·취업 관련 글 추천해줘"},
    {"label": "🌐 교육생 서비스", "value": "교육생 서비스 관련 글 추천해줘"},
]


async def say(text: str):
    """고정 문구를 한 글자씩 흘려보낸다. 타이핑 효과는 그대로 남는다."""
    for piece in text:
        yield ev.token(piece)

# HTTPBearer를 쓰면 /docs(Swagger)에 "Authorize" 자물쇠 버튼이 생겨 토큰을 붙여 테스트할 수 있다.
# 헤더를 직접 읽으면 FastAPI가 인증이 있는 줄 몰라서 그 버튼이 안 나온다.
bearer = HTTPBearer(auto_error=False, description="SKALA Hub 로그인 토큰")


def current_user(cred: HTTPAuthorizationCredentials | None = Depends(bearer)) -> str:
    """토큰에서 slack_id를 꺼낸다.

    Spring이 발급한 토큰을 그대로 검증한다 (같은 JWT_SECRET을 공유).
    토큰의 subject가 slack_id다 (JwtService:32).
    """
    if cred is None:
        raise HTTPException(401, "로그인이 필요합니다")

    settings.require("jwt_secret")
    try:
        payload = jwt.decode(cred.credentials, settings.jwt_secret, algorithms=JWT_ALGORITHMS)
    except jwt.ExpiredSignatureError:
        raise HTTPException(401, "로그인이 만료되었습니다. 다시 로그인해 주세요")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "로그인 정보를 확인할 수 없습니다")

    slack_id = payload.get("sub")
    if not slack_id:
        raise HTTPException(401, "로그인 정보를 확인할 수 없습니다")
    return slack_id


async def _respond(req: ev.ChatRequest, rec: dict[str, Any]) -> AsyncIterator[str]:
    """질문을 알맞은 길로 보낸다.

    `rec`에 로그로 남길 값을 채워 넣는다. usage/done은 여기서 보내지 않는다
    - 어느 길로 갔든 마지막 처리(로그 저장 → 잔여 횟수 → done)가 같아야 하므로
      run()에서 한 곳에서만 보낸다.
    """
    path = route(req.question)
    rec["route"] = path
    log.info("route=%s q=%r", path, req.question[:40])

    # --- 검색도 LLM도 필요 없는 길 ---------------------------------
    if path in ("about", "how", "reject", "block", "person"):
        fixed = {
            "about": ABOUT_TEXT, "how": HOW_TEXT, "reject": REJECT_TEXT,
            "block": BLOCK_TEXT, "person": PERSON_TEXT,
        }[path]
        # 차단된 질문은 의도 분석 단계에서 멈춘 것처럼 보이게 한다 (검색을 돌리지 않았으므로)
        thinking = path in ("reject", "block", "person")
        yield ev.status("intent" if thinking else "compose", "질문 의도 분석 중" if thinking else "답변 정리하는 중")
        async for piece in say(fixed):
            yield piece
        rec["answer"] = fixed
        return

    if path == "clarify":
        # 되묻기는 답이 아니다. llm_called=False라 한도도 차감되지 않는다.
        yield ev.clarify("어떤 종류의 글을 찾고 있나요?", CLARIFY_OPTIONS)
        return

    # --- DB 집계로 답하는 길 ---------------------------------------
    if path == "popular":
        yield ev.status("search_posts", "반응 많은 글 찾는 중")
        # "이번 달"이 아니면 전체 기간에서 고른다
        days = 30 if re.search(r"이번\s*달|최근|요즘", req.question) else None
        posts = popular_posts(days=days)
        if not posts:
            text = "아직 보여드릴 글이 없어요."
            async for piece in say(text):
                yield piece
            rec["answer"] = text
            return
        head = "이번 달" if days else "지금까지"
        text = f"{head} 반응이 많았던 글 {len(posts)}개예요."
        async for piece in say(text):
            yield piece
        yield ev.sources(posts)
        rec["answer"] = text
        rec["post_ids"] = [p["id"] for p in posts]  # 유사도는 없다 - SQL로 고른 것
        return

    if path == "keywords":
        # 주제 키워드는 DB에 아직 없다 (tags는 글 '형식'이라 주제어가 아니다).
        # Step 9-b에서 AI 제목 생성기가 키워드도 뽑아 컬럼에 넣으면 이 길이 열린다.
        text = (
            "요즘 어떤 주제가 많이 올라오는지는 아직 준비 중이에요.\n"
            "대신 반응이 많았던 글은 보여드릴 수 있어요."
        )
        async for piece in say(text):
            yield piece
        yield ev.offer("이번 달 인기 글을 보여드릴까요?", "이번 달 인기 게시글 추천해줘")
        rec["answer"] = text
        return

    # --- 게시글을 찾아 답하는 길 (기본) -----------------------------
    yield ev.status("search_posts", f"{req.context}에서 게시글 검색 중")
    # 지금 보고 있는 탭 안에서 먼저 찾는다 (없으면 rag_service가 전체로 넓힌다)
    docs = await search(req.question, category=req.category)
    in_scope = bool(req.category) and all(
        d.metadata.get("category") == req.category for d in docs
    )
    # 조각 순서 그대로 남긴다 (글 단위로 합치지 않음).
    # 같은 글이 두 번 나오는 것 자체가 측정 대상이다 - "상위 5개 중 서로 다른 글 몇 개인가".
    rec["post_ids"] = [d.metadata["post_id"] for d in docs]
    rec["scores"] = [d.metadata.get("score") for d in docs]

    if not docs:
        yield ev.status("compose", "답변 정리하는 중")
        async for piece in say(NOT_FOUND_TEXT):
            yield piece
        rec["answer"] = NOT_FOUND_TEXT
        rec["abstained"] = True
        return

    yield ev.status("compose", "답변 정리하는 중")
    # 흘려보내면서 동시에 모아둔다 - 로그에 전체 답변이 필요하다
    pieces: list[str] = []
    rec["llm_called"] = True  # 여기서부터 비용이 발생한다. 중간에 터져도 센다
    async for piece in stream_answer(req.question, docs):
        pieces.append(piece)
        yield ev.token(piece)
    answer_text = "".join(pieces)
    rec["answer"] = answer_text
    # 프롬프트가 쓰라고 지시한 문구가 그대로 나왔는지로 판정한다 (generator.ABSTAIN_PHRASE).
    # 완벽한 판정은 아니다 - 모델이 문구를 바꿔 쓰면 놓친다. 로그를 읽을 때 보정한다.
    rec["abstained"] = ABSTAIN_PHRASE in answer_text

    # 탭 밖에서 찾아온 경우엔 그 사실을 알려준다. 모르면 "왜 다른 카테고리 글이 나오지?"가 된다.
    if req.category and not in_scope:
        async for piece in say(f"\n\n({req.context}에는 없어서 전체에서 찾았어요)"):
            yield piece

    yield ev.sources(_to_sources(docs))


async def run(req: ev.ChatRequest, slack_id: str) -> AsyncIterator[str]:
    """공개 범위·한도를 확인하고, 답한 뒤 로그를 남긴다.

    에러가 나도 연결을 끊지 않고 error 이벤트로 알린다.
    끊어버리면 화면이 "답변 쓰는 중"에 영영 멈춘다.
    """
    rec: dict[str, Any] = {"category": req.category, "llm_called": False, "abstained": False}
    started = time.perf_counter()
    used = 0

    # 공개 범위 밖이면 로그를 남기지 않는다 - 질문이 아니라 접근 거부라서 분석 가치가 없다.
    scope = log_service.user_scope(slack_id)
    if not log_service.can_use_chat(slack_id, scope):
        log.info("공개 범위 밖 접근 (user=%s, scope=%s)", slack_id, scope)
        async for piece in say(CLOSED_TEXT):
            yield piece
        yield ev.done()
        return

    try:
        used = log_service.used_today(slack_id)
        if used >= settings.chat_daily_limit:
            # 한도에 걸린 것도 기록한다 - "10회가 맞는 숫자인가"를 판단할 근거가 된다
            rec["error"] = "daily_limit"
            async for piece in say(LIMIT_TEXT):
                yield piece
        else:
            async for piece in _respond(req, rec):
                yield piece
    except Exception as e:  # 모델 장애·한도 초과 등 - 화면에는 짧게, 로그에는 자세히
        log.exception("chat 실패 (user=%s, q=%r)", slack_id, req.question[:40])
        rec["error"] = f"{type(e).__name__}: {e}"[:500]
        yield ev.error("잠시 문제가 생겼어요. 다시 시도해 주세요.")

    # 어느 길로 갔든 여기로 모인다. 저장이 실패하면 log_id가 None이고 답변은 멀쩡하다.
    rec["latency_ms"] = int((time.perf_counter() - started) * 1000)
    log_id = log_service.save(slack_id=slack_id, question=req.question, **rec)
    spent = used + (1 if rec["llm_called"] else 0)
    yield ev.usage(max(0, settings.chat_daily_limit - spent))
    yield ev.done(log_id)


@router.post("/chat")
async def chat(req: ev.ChatRequest, slack_id: str = Depends(current_user)):
    return StreamingResponse(
        run(req, slack_id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # 프록시가 스트림을 모아뒀다 보내지 않게
        },
    )


@router.get("/chat/access")
async def access(slack_id: str = Depends(current_user)):
    """이 사용자에게 챗봇을 열어줄지와 남은 횟수.

    화면이 이걸 보고 챗봇 버튼을 띄울지 정한다. 공개 범위 규칙을 프론트에 복사하지 않으려고
    서버에 물어보게 했다 - 두 곳에 두면 .env만 바꿨을 때 조용히 어긋난다.

    곁다리 효과가 둘 있다.
    - 하드코딩돼 있던 "오늘 n/10회"의 10을 서버가 알려준다
    - AI 서버가 꺼져 있으면 호출이 실패해 버튼이 안 뜬다. 눌러도 안 되는 버튼을
      보여주는 것보다 낫다 (터널로 띄우는 1차 베타에선 자주 생길 상황이다)
    """
    scope = log_service.user_scope(slack_id)
    allowed = log_service.can_use_chat(slack_id, scope)
    limit = settings.chat_daily_limit
    remaining = max(0, limit - log_service.used_today(slack_id)) if allowed else 0
    return {"allowed": allowed, "limit": limit, "remaining": remaining}


@router.post("/chat/{log_id}/feedback")
async def feedback(
    log_id: int, body: ev.FeedbackRequest, slack_id: str = Depends(current_user)
):
    """👍 / 👎. log_id는 done 이벤트가 알려준 값이다.

    남의 답변에는 달 수 없다 (log_service가 user_slack_id까지 확인한다).
    """
    if not log_service.set_feedback(log_id, slack_id, body.value, body.reason):
        raise HTTPException(404, "해당 답변을 찾을 수 없습니다")
    return {"ok": True}
