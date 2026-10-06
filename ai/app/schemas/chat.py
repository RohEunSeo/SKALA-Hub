"""챗봇 요청/응답 모델 + 화면과 주고받는 이벤트 형식.

**이 파일이 프론트엔드와의 계약서다.**
`frontend/src/api/chat.js`가 흉내내던 이벤트 10종을 여기서 그대로 만든다.
형식이 맞으면 `stores/chat.js`와 `ChatPanel.vue`는 한 줄도 고치지 않아도 된다.
"""
import json
from typing import Any, Literal

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """화면에서 올라오는 질문.

    context  : 사람이 읽는 표시용 라벨 ("전체 피드", "학습 자료 피드")
    category : 검색 범위를 좁힐 실제 값 ("학습자료", null이면 전체)
    둘을 따로 받는 이유 - 표시용 문구와 필터 값이 다르기 때문.
    """
    question: str = Field(min_length=1, max_length=500)
    context: str = "전체 피드"
    category: str | None = None
    # 몇 개를 찾을지. 비우면 서버 기본값(5).
    # "더 찾아볼까요?"를 수락했을 때만 채워져 오고, 그 값은 **서버가 offer로 알려준 것**이다
    # (화면에 숫자를 박아두면 서버에서 바꿔도 안 바뀐다).
    limit: int | None = Field(default=None, ge=1, le=50)
    # 상세 페이지에서 챗봇을 열면 "지금 이 글을 보고 있다"를 알려준다.
    # 이게 있으면 벡터 검색을 건너뛰고 그 글만 다룬다 - 엉뚱한 글이 섞일 수가 없다.
    post_id: int | None = None


class FeedbackRequest(BaseModel):
    """👍 / 👎. 어느 답변인지는 URL의 log_id로 알린다 (done 이벤트가 알려준 값)."""
    value: int = Field(description="좋아요 1 / 싫어요 -1")
    reason: str | None = Field(default=None, max_length=200, description="👎일 때 고른 이유")


# --- 화면으로 내려보내는 이벤트 ---------------------------------------------
# 목업이 정한 순서: status → token… → sources → save_proposal → usage → done
# 중간에 끊기면 error. 되묻기는 clarify, 후속 제안은 offer.

def sse(payload: dict[str, Any]) -> str:
    """SSE 한 줄로 포장한다.

    `data: {...}\\n\\n` 형식이어야 브라우저의 EventSource/fetch 스트림이 한 건으로 끊어 읽는다.
    한글이 깨지지 않게 ensure_ascii=False.
    """
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


# status.tool - 화면이 이 값으로 로딩 그림을 고른다 (ChatPanel.vue:229~232).
# 특히 search_posts 는 피드의 "검색 중" 모션까지 켠다 (FeedView.vue:145).
Tool = Literal["intent", "search_posts", "think", "compose"]


def status(tool: Tool, text: str) -> str:
    """진행 상태. text는 서버가 만든 문구를 화면이 그대로 보여준다."""
    return sse({"type": "status", "tool": tool, "text": text})


def token(text: str) -> str:
    """답변 조각. 화면이 이어붙이므로(current.text += ev.text) **차이분만** 보낸다."""
    return sse({"type": "token", "text": text})


def sources(posts: list[dict[str, Any]]) -> str:
    """근거 글 목록. 순서가 곧 관련도 순이고, 피드도 이 순서로 글을 올린다."""
    return sse({"type": "sources", "posts": posts})


def save_proposal(post_ids: list[int]) -> str:
    """저장 제안. 폴더 목록은 화면이 자기 스토어에서 읽으므로 보내지 않는다."""
    return sse({"type": "save_proposal", "folders": [], "postIds": post_ids})


def usage(remaining: int) -> str:
    """남은 횟수. 목업엔 이 필드가 없어 화면이 혼자 1씩 빼고 있었다 - 서버가 알려준다."""
    return sse({"type": "usage", "remaining": remaining})


def error(message: str) -> str:
    return sse({"type": "error", "message": message})


def done(log_id: int | None = None) -> str:
    """끝. log_id는 👍👎를 어느 답변에 달지 화면이 알아야 해서 같이 보낸다.

    로그 저장이 실패하면 None이 간다 - 그때는 피드백 버튼만 동작하지 않고
    답변 자체는 멀쩡하다 (로그는 답변을 막지 않는다).
    """
    return sse({"type": "done", "logId": log_id})


def keywords(items: list[dict[str, Any]]) -> str:
    """키워드 집계 결과. items = [{"word": "SQLD", "count": 8}, ...]"""
    return sse({"type": "keywords", "items": items})


def clarify(question: str, options: list[dict[str, str]]) -> str:
    """되묻기. options의 value는 **다음 턴에 그대로 다시 물어볼 질문 문장**이다.

    화면이 선택지를 누르면 그 문장으로 ask()를 다시 부른다 (stores/chat.js:121).
    """
    return sse({"type": "clarify", "question": question, "options": options})


def offer(question: str, query: str, scope: str | None = None, limit: int | None = None) -> str:
    """후속 제안. "네"를 누르면 query 문장으로 다시 물어본다 (stores/chat.js).

    scope="all" 이면 **카테고리 필터를 풀고** 다시 묻는다.
    탭 안에 답이 없어서 전체로 넓히는 경우에 쓴다 - 안 그러면 같은 탭에서 또 못 찾는다.
    scope="more" 면 같은 조건으로 limit개까지 더 찾는다.
    """
    return sse({"type": "offer", "question": question, "query": query,
                "scope": scope, "limit": limit})
