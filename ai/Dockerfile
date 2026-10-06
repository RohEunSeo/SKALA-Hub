# 수업 실습(langchain 1.x)이 3.12를 아직 안 받아서 3.11로 고정 - pyproject.toml의 requires-python과 같아야 한다
FROM python:3.11-slim

# uv 설치 (pip보다 훨씬 빠름 - Cloud Build 시간을 줄이려는 목적)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

WORKDIR /app

# 의존성만 먼저 복사해서 레이어 캐시를 살린다 - 코드만 고칠 땐 재설치가 일어나지 않음
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY . .

ENV PATH="/app/.venv/bin:$PATH"

# Cloud Run이 PORT=8080을 주입한다 (고정하면 헬스체크가 실패함). 로컬은 8000으로 뜬다.
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}"]
