"""환경변수 설정 (루트 .env 공유).

이 파일이 .env를 읽는 **유일한 곳**이다. 다른 모듈은 os.getenv를 쓰지 말고
`from app.config import settings` 로 가져다 쓴다.
키를 코드 여기저기서 꺼내 쓰면 나중에 "이 키를 어디서 쓰지?"를 못 찾는다.
"""
from functools import lru_cache
from pathlib import Path
from urllib.parse import quote_plus, urlparse

from pydantic_settings import BaseSettings, SettingsConfigDict

AI_DIR = Path(__file__).resolve().parent.parent  # skala-hub/ai
ROOT_DIR = AI_DIR.parent                          # skala-hub


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=ROOT_DIR / ".env",  # Spring 백엔드와 같은 .env - JWT_SECRET이 같아야 토큰 검증이 된다
        env_file_encoding="utf-8",
        extra="ignore",  # 루트 .env엔 슬랙/구글 변수도 있으므로 모르는 키는 무시
    )

    # --- 생성 모델 (교체 지점은 core/generator.py 한 곳) ---
    llm_provider: str = "google"  # google | anthropic
    llm_model: str = "gemini-3.5-flash-lite"  # 2.5-flash-lite는 신규 사용자에게 닫힘(404)
    # 1순위가 한도 초과(429)면 순서대로 넘어간다. 무료 한도는 **모델마다 따로** 센다.
    # 주의: 이름에 -latest가 붙은 건 **별칭**이라 본 모델과 쿼터를 공유한다 → 넣어도 소용없다
    llm_fallbacks: str = "gemini-3.1-flash-lite,gemini-3.5-flash"
    # 0으로 둬야 한도 초과 시 **즉시** 다음 모델로 넘어간다.
    # 기본값이면 구글 클라이언트가 내부적으로 재시도하느라 폴백까지 37초가 걸렸다(실측).
    llm_retries: int = 0
    gemini_api_key: str = ""
    claude_api_key: str = ""

    # --- 임베딩 (바꾸면 전체 재인덱싱 필요) ---
    embedding_model: str = "text-embedding-3-small"
    openai_api_key: str = ""

    # --- 벡터 저장소 (교체 지점은 core/retriever.py 한 곳) ---
    vector_store: str = "faiss"  # faiss(평가용 고정 인덱스) | pgvector(실사용)
    faiss_index_path: str = "data/faiss_index"

    # --- Supabase ---
    # REST는 service_role 키로도 posts SELECT가 403이 나서 Postgres로 직접 붙는다.
    # Spring 백엔드(application.yml)와 같은 풀러를 쓴다. 직접 연결(db.<ref>.supabase.co)은
    # 이 프로젝트에선 DNS에 없다.
    supabase_url: str = ""
    supabase_db_host: str = ""  # aws-1-ap-northeast-2.pooler.supabase.com
    supabase_db_port: int = 6543  # 트랜잭션 모드. 세션 모드(5432)는 동시접속 제한에 쉽게 걸림
    supabase_db_user: str = ""  # postgres.<project_ref> - 직접 연결과 형식이 다름
    supabase_db_password: str = ""
    database_url: str = ""  # 채우면 위 값들 대신 이걸 그대로 쓴다

    # --- 인증 (Step 7~) ---
    jwt_secret: str = ""
    frontend_url: str = "http://localhost:5173"
    # CORS 허용 출처. 비우면 frontend_url 하나만 허용한다.
    # frontend_url에 콤마를 넣어 해결하지 않는 이유: 그 키는 Spring과 공유하고
    # Spring은 List.of(frontendUrl)로 단일 값만 받아서 콤마가 들어가면 깨진다.
    cors_origins: str = ""

    # --- 챗봇 운영 (1차 베타) ---
    chat_daily_limit: int = 10  # 하루 LLM 호출 수. 비용이 나가면 이 값만 줄인다
    # 공개 범위. **둘 다 비우면 전체 공개**가 된다
    # - 즉 340명 오픈은 환경변수를 지우는 일이고, 코드 배포가 아니다
    # - class_num은 DB에 '5반' 형태의 문자열로 들어있다(정수가 아님)
    chat_allowed_campus: str = ""   # 예: 판교
    chat_allowed_classes: str = ""  # 예: 5반  (여러 반은 '5반,6반')
    # 반과 무관하게 열어줄 사람들(slack_id, 쉼표로 구분).
    # 매니저·교수님은 DB에 campus/class_num이 비어 있어 반 기준으로는 통과할 수 없다.
    # role 전체를 여는 것(manager 9명)보다 지목하는 쪽이 정확하다.
    chat_allowed_users: str = ""
    # 로그를 나중에 읽을 수 있게 하는 표식. 인덱스를 다시 만들면 이 값을 올린다.
    # 없으면 "이 로그는 어느 인덱스에서 나온 건가"를 사후에 알 수 없다.
    index_version: str = "baseline-v0"

    @property
    def faiss_dir(self) -> Path:
        """실행 위치와 무관하게 항상 ai/data/faiss_index 를 가리킨다."""
        path = Path(self.faiss_index_path)
        return path if path.is_absolute() else AI_DIR / path

    @property
    def cors_allow_origins(self) -> list[str]:
        """CORS 허용 목록. cors_origins가 비면 frontend_url을 쓴다. 로컬은 항상 허용."""
        given = [o.strip().rstrip("/") for o in self.cors_origins.split(",") if o.strip()]
        if not given:
            given = [self.frontend_url.rstrip("/")]
        return list(dict.fromkeys(given + ["http://localhost:5173"]))

    @property
    def allowed_classes(self) -> set[str]:
        """공개할 반 목록. 비어 있으면 '제한 없음'을 뜻한다."""
        return {c.strip() for c in self.chat_allowed_classes.split(",") if c.strip()}

    @property
    def fallback_models(self) -> list[str]:
        """1순위 다음에 시도할 모델들. 1순위와 중복되는 건 뺀다."""
        seen = {self.llm_model}
        out = []
        for m in self.llm_fallbacks.split(","):
            m = m.strip()
            if m and m not in seen:
                seen.add(m)
                out.append(m)
        return out

    @property
    def allowed_users(self) -> set[str]:
        """반과 무관하게 열어줄 slack_id 목록."""
        return {u.strip() for u in self.chat_allowed_users.split(",") if u.strip()}

    @property
    def project_ref(self) -> str:
        """https://<ref>.supabase.co 에서 프로젝트 식별자만 꺼낸다."""
        host = urlparse(self.supabase_url).hostname
        return host.split(".")[0] if host else ""

    @property
    def postgres_dsn(self) -> str:
        """Supabase Postgres 접속 문자열.

        DATABASE_URL이 있으면 그대로 쓴다. 없으면 풀러 주소로 조립한다
        (Spring 백엔드 application.yml과 같은 호스트/유저 형식).
        """
        if self.database_url:
            return self.database_url
        self.require("supabase_db_host", "supabase_db_password")
        user = self.supabase_db_user or f"postgres.{self.project_ref}"
        password = quote_plus(self.supabase_db_password)  # 비밀번호에 특수문자가 있어도 깨지지 않게
        return (
            f"postgresql://{user}:{password}"
            f"@{self.supabase_db_host}:{self.supabase_db_port}/postgres"
        )

    def require(self, *names: str) -> None:
        """필요한 설정이 비어 있으면 바로 알려준다.

        없는 채로 진행하면 한참 뒤에 401/빈 결과로 터져서 원인을 찾기 어렵다.
        """
        missing = [n for n in names if not getattr(self, n, "")]
        if missing:
            raise RuntimeError(
                f"루트 .env에 다음 값이 비어 있습니다: {', '.join(n.upper() for n in missing)}"
            )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
