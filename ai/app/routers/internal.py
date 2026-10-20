"""Spring 백엔드 전용 엔드포인트. 사람이 아니라 서버가 서버를 부르는 구간이다.

로그인 사용자가 아니므로 chat.py의 JWT 로그인 검증(current_user)을 그대로 쓸 수
없다. 대신 Spring과 이미 공유하고 있는 JWT_SECRET 값을 헤더로 그대로 비교한다 -
새 비밀값을 하나 더 만들지 않기 위해서다(로그인 토큰을 발급/검증하는 게 아니라
단순 비밀값 일치 확인).
"""
import logging

from fastapi import APIRouter, Depends, Header, HTTPException

from app.config import settings
from app.core.indexer import reindex_post

log = logging.getLogger(__name__)
router = APIRouter()


def require_internal_secret(x_internal_secret: str | None = Header(None)) -> None:
    settings.require("jwt_secret")
    if x_internal_secret != settings.jwt_secret:
        raise HTTPException(401, "내부 전용 엔드포인트입니다")


@router.post("/internal/reindex-post")
def reindex_post_endpoint(post_id: int, _: None = Depends(require_internal_secret)) -> dict:
    try:
        saved = reindex_post(post_id)
    except Exception:
        log.exception("post_id=%s 재인덱싱 실패", post_id)
        raise HTTPException(500, "인덱싱 실패")
    return {"post_id": post_id, "chunks": saved}
