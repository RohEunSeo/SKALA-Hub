# SKALA Hub

SKALA 부트캠프 교육생 슬랙 정보공유 채널 아카이빙 플랫폼.

## 기술 스택

- 프론트: Vue.js 3 (Vite + Pinia + Vue Router)
- 백엔드: Spring Boot 4 (Java 21)
- DB: Supabase (PostgreSQL)
- AI 분류: Claude Haiku API

## 프로젝트 구조

```
skala-hub/
├── frontend/   # Vue.js 3 (Vite)
├── backend/    # Spring Boot 4
├── design/     # UI 목업
└── README.md
```

## 환경변수

루트에 `.env` 파일을 만들고 아래 값을 채워주세요. (`.env`는 절대 커밋하지 마세요)

```
SLACK_CLIENT_ID=
SLACK_CLIENT_SECRET=
SLACK_USER_TOKEN=
SLACK_CHANNEL_ID=C0BHGGH7PT3
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
# 백엔드 JPA/JDBC 접속용 DB 비밀번호 (호스트/유저는 application.yml에 고정됨 - Session pooler 주소)
SUPABASE_DB_PASSWORD=
CLAUDE_API_KEY=
JWT_SECRET=
JWT_EXPIRATION=86400000
```

프론트엔드는 `frontend/.env.example`을 복사해 `frontend/.env`를 만들고 `VITE_API_BASE_URL`(백엔드 주소)을 채워주세요.

## 로컬 실행 방법

### 백엔드 (Spring Boot)

`backend/src/main/resources/application.properties`가 위 환경변수를 그대로 참조하므로, 실행 전 셸에 환경변수를 로드해야 합니다.

```bash
cd backend
./gradlew bootRun   # Windows는 gradlew.bat bootRun (또는 .\gradlew.bat bootRun)
```

### 프론트엔드 (Vue 3 + Vite)

```bash
cd frontend
npm install
npm run dev
```

기본 개발 서버 주소는 http://localhost:5173 입니다.

## 배포

- 프론트: Vercel
- 백엔드: Render (콜드스타트 방지를 위해 10분마다 헬스체크 핑 필요)
