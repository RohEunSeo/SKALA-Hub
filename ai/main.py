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
    allow_origins=[settings.frontend_url, "http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

app.include_router(chat.router, prefix="/api")


@app.get("/health")
def health() -> dict:
    """Render 헬스체크 + 콜드스타트 깨우기용. 인덱스가 있는지도 같이 알려준다."""
    return {
        "ok": True,
        "model": settings.llm_model,
        "vector_store": settings.vector_store,
        "index_ready": settings.faiss_dir.exists(),
    }
