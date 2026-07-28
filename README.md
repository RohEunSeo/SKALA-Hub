# SKALA Hub

SKALA 부트캠프 교육생 슬랙 정보공유 채널 아카이빙 플랫폼.

## 기술 스택

- 프론트: Vue.js 3 (Vite + Pinia + Vue Router) → Vercel 배포
- 백엔드: Spring Boot 4 (Java 21) → Render 배포 (Docker)
- DB: Supabase (PostgreSQL)
- AI 분류: Claude Haiku API

## 프로젝트 구조

```
skala-hub/
├── frontend/               # Vue.js 3 (Vite)
│   └── vercel.json         # Vercel 배포 설정 (SPA 라우팅)
├── backend/                # Spring Boot 4
│   ├── Dockerfile          # Render 배포용 멀티스테이지 빌드
│   └── src/main/resources/application.yml
├── render.yaml             # Render Blueprint (백엔드 배포 설정)
├── .github/workflows/      # 콜드스타트 방지 스케줄러
├── design/                 # UI 목업
└── README.md
```

## 환경변수

루트에 `.env` 파일을 만들고 아래 값을 채워주세요. (`.env`는 절대 커밋하지 마세요)

```
SLACK_CLIENT_ID=
SLACK_CLIENT_SECRET=
SLACK_USER_TOKEN=
SLACK_CHANNEL_ID=C0BHGGH7PT3
SUPABASE_DB_PASSWORD=
CLAUDE_API_KEY=
JWT_SECRET=
JWT_EXPIRATION=86400000
```

프론트엔드는 `frontend/.env.example`을 복사해 `frontend/.env`를 만들고 `VITE_API_BASE_URL`(백엔드 주소)을 채워주세요.

## 로컬 실행 방법

### 백엔드 (Spring Boot)

`backend/src/main/resources/application.yml`이 위 환경변수를 그대로 참조하므로, 실행 전 셸에 환경변수를 로드해야 합니다.

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

### 1. 백엔드 (Render)

1. [render.com](https://render.com) 접속 → **New +** → **Blueprint** → 이 저장소 연결
2. 저장소 루트의 `render.yaml`을 자동으로 인식해 `skala-hub-backend` 서비스가 생성됨 (Docker 빌드, `backend/Dockerfile` 사용)
3. 생성 화면에서 아래 환경변수를 입력 (`render.yaml`에 `sync: false`로 표시된 값들 - 대시보드에만 저장되고 커밋되지 않음)

| 환경변수 | 값 |
|---|---|
| `SUPABASE_DB_PASSWORD` | Supabase DB 비밀번호 |
| `SLACK_CLIENT_ID` | 슬랙 앱 Client ID |
| `SLACK_CLIENT_SECRET` | 슬랙 앱 Client Secret |
| `SLACK_USER_TOKEN` | 슬랙 User OAuth Token (`xoxp-...`) |
| `SLACK_REDIRECT_URI` | `https://<render-백엔드-주소>/api/auth/slack/callback` |
| `CLAUDE_API_KEY` | Claude API 키 |
| `JWT_SECRET` | 랜덤 문자열 |
| `FRONTEND_URL` | `https://<vercel-프론트-주소>` (CORS 허용 + OAuth 로그인 후 리다이렉트 대상) |

4. 배포 완료 후 발급된 백엔드 URL(`https://skala-hub-backend-xxxx.onrender.com`)을 복사
5. **슬랙 앱 설정**(api.slack.com/apps → OAuth & Permissions)에도 `SLACK_REDIRECT_URI`와 동일한 콜백 URL을 등록해야 로그인이 동작함

### 2. 프론트엔드 (Vercel)

1. [vercel.com](https://vercel.com) 접속 → **Add New** → **Project** → 이 저장소 연결
2. **Root Directory**를 `frontend`로 지정 (Framework Preset은 Vite 자동 인식)
3. 환경변수 등록

| 환경변수 | 값 |
|---|---|
| `VITE_API_BASE_URL` | 1번에서 복사한 Render 백엔드 URL |

4. Deploy → 완료 후 발급된 Vercel URL을 Render의 `FRONTEND_URL`에 입력하고 백엔드를 재배포(Manual Deploy)해야 CORS/로그인 리다이렉트가 정상 동작함

### 3. 콜드스타트 방지

Render 무료 플랜은 일정 시간 요청이 없으면 슬립 상태가 됩니다. `.github/workflows/keep-alive.yml`이 10분마다 백엔드 `/api/health`를 호출해 깨어있게 유지합니다.

- GitHub 저장소 → **Settings → Secrets and variables → Actions → Variables** 탭에서
  `RENDER_BACKEND_URL` = Render 백엔드 URL (`https://skala-hub-backend-xxxx.onrender.com`) 등록
- 저장소가 GitHub Actions를 지원하는 상태여야 스케줄이 동작함 (Actions 탭에서 활성화 확인)

### 4. CORS

백엔드는 `app.frontend-url`(`FRONTEND_URL` 환경변수) 하나만 허용 origin으로 사용합니다. Vercel 배포 URL을 `FRONTEND_URL`에 정확히 입력하면 별도 코드 수정 없이 CORS가 허용됩니다.
