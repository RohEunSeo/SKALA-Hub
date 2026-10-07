"""FastAPI 앱 진입점.

    uv run uvicorn main:app --reload        개발
    (배포는 Dockerfile이 ${PORT}로 띄운다)
"""
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import chat

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

app = FastAPI(title="SKALA Hub AI", docs_url="/docs")

# 프론트(Vercel/로컬)에서 직접 부르므로 출처를 열어준다.
# Authorization 헤더를 실어 보내야 해서 allow_headers에 포함된다.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allow_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

app.include_router(chat.router, prefix="/api")


@app.on_event("startup")
def warn_bad_models() -> None:
    """설정한 모델 이름이 틀렸으면 뜰 때 바로 알린다.

    오타는 404를 내는데 404는 폴백이 받아주지 않아서, 조용히 두면
    "왜 챗봇이 전부 에러지?"를 한참 헤매게 된다.
    """
    from app.core.generator import check_models

    bad = check_models()
    if bad:
        logging.getLogger("startup").error(
            "⚠️ .env의 모델 이름이 잘못됐습니다: %s — 챗봇이 404로 멈춥니다", ", ".join(bad)
        )


def _index_size() -> int | None:
    """지금 쓰는 저장소에 조각이 몇 개 있는지. 모르면 None.

    pgvector를 쓰는데 FAISS 폴더를 보면 항상 '없음'이 나와서,
    정작 인덱스가 빈 상황과 구분이 안 됐다. 그래서 저장소별로 나눠 본다.
    """
    if settings.vector_store == "pgvector":
        try:
            from app.db import fetch_all
            return fetch_all("select count(*) as n from post_chunks")[0]["n"]
        except Exception:
            return None  # 헬스체크가 DB 때문에 실패하면 안 된다
    return len(list(settings.faiss_dir.glob("*"))) if settings.faiss_dir.exists() else 0


@app.get("/health")
def health() -> dict:
    """콜드스타트 깨우기용. 지금 쓰는 인덱스에 내용이 있는지도 같이 알려준다."""
    n = _index_size()
    return {
        "ok": True,
        "model": settings.llm_model,
        "vector_store": settings.vector_store,
        "index_ready": bool(n),  # None(조회 실패)도 false로 본다
        "index_size": n,
    }
