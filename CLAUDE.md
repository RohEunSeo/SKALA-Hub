# SKALA Hub

## 서비스
SKALA 부트캠프 교육생(340명, 5개월) 슬랙 정보공유 채널 아카이빙 플랫폼.
1인 바이브코딩 → 코드 단순하게, 한국어 주석 필수, 환경변수 하드코딩 금지.

## 기술 스택
- 프론트: Vue.js 3 (Vite + Pinia + Vue Router) → Vercel 배포
- 백엔드: Spring Boot 3 (Java 17) → Render 배포
- DB: Supabase (PostgreSQL)
- AI 분류: Claude Haiku API

## 환경변수

SLACK_CLIENT_ID=
SLACK_CLIENT_SECRET=
SLACK_USER_TOKEN=
SLACK_CHANNEL_ID=C0BHGGH7PT3
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
CLAUDE_API_KEY=
JWT_SECRET=
JWT_EXPIRATION=86400000


## DB 테이블
```sql
CREATE TABLE users (
  slack_id VARCHAR(50) PRIMARY KEY,
  name VARCHAR(100), cohort VARCHAR(10),
  campus VARCHAR(20), class_num VARCHAR(10),
  role VARCHAR(20) DEFAULT 'user',
  profile_img VARCHAR(500),
  created_at TIMESTAMP DEFAULT NOW(),
  last_login TIMESTAMP
);

CREATE TABLE posts (
  id BIGSERIAL PRIMARY KEY,
  slack_ts VARCHAR(50) UNIQUE,
  user_slack_id VARCHAR(50), user_name VARCHAR(100),
  is_instructor BOOLEAN DEFAULT false,
  content TEXT, ai_title VARCHAR(200),
  category VARCHAR(50), tags TEXT[],
  reaction_count INT DEFAULT 0, reply_count INT DEFAULT 0,
  is_deleted BOOLEAN DEFAULT false, is_pinned BOOLEAN DEFAULT false,
  created_at TIMESTAMP, synced_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE replies (
  id BIGSERIAL PRIMARY KEY,
  post_id BIGINT REFERENCES posts(id),
  slack_ts VARCHAR(50), user_slack_id VARCHAR(50),
  user_name VARCHAR(100), content TEXT, created_at TIMESTAMP
);

CREATE TABLE bookmarks (
  id BIGSERIAL PRIMARY KEY,
  user_id VARCHAR(50) REFERENCES users(slack_id),
  post_id BIGINT REFERENCES posts(id),
  saved_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(user_id, post_id)
);
```

## 카테고리

상위: 개발도구 / 학습자료 / 취업자격증 / 교육생서비스 / 교수님 / 기타
학습자료 하위태그: 영상 / 아티클 / 깃허브
교수님 판단: user_name에 "교수" 또는 "전임" 포함 시


## Slack API (검증 완료)
- Channel ID: C0BHGGH7PT3 (private 채널)
- conversations.history → 전체 게시글 (페이지네이션)
- conversations.replies → 스레드 댓글
- users.info → 유저 이름 (캐싱 필수)
- 이미지: url_private → 백엔드 프록시로 제공
- 슬랙 딥링크: https://theskala.slack.com/archives/C0BHGGH7PT3/p{ts}

## 동기화 전략
- 최초 1회: 전체 수집 → DB 저장
- 이후: Slack Event API 실시간 감지
- 이모지/댓글: 30분마다 스케줄러
- 게시글 수정/삭제 이벤트 처리

## 컬러

메인: 
#4A3F8F / 서브: 
#6C5CE7
배경: 
#FAFAFA / 카드: 
#FFFFFF
텍스트: 
#1A1A2E / 보조: 
#636E72
코드블록: 
#1A1A2E


## 디자인
design/SKALA_Hub_standalone.html 이 UI 목업.
Vue 컴포넌트 만들 때 이 파일 HTML/CSS 그대로 참고해서 구현.

## 프로젝트 구조

skala-hub/
├── frontend/ # Vue.js 3
│ └── src/
│ ├── views/ # LoginView, HomeView, FeedView, MyPageView, AdminView
│ ├── components/ # PostCard, Sidebar, SearchBar, CategoryFilter
│ ├── stores/ # auth, posts, bookmarks
│ ├── router/
│ └── api/ # slack.js, posts.js, auth.js
├── backend/ # Spring Boot 3
│ └── src/main/java/com/skalahub/
│ ├── controller/
│ ├── service/
│ ├── repository/
│ ├── entity/
│ └── config/
├── design/
│ └── SKALA_Hub_standalone.html
├── CLAUDE.md
└── .env


## 관리자 기능 (role=admin)
- /admin 페이지 별도 접근
- 게시글 카테고리/태그/제목 수동 수정
- 핀 고정/해제
- 미분류 게시글 목록 + 일괄 분류
- 전체 동기화 버튼

## 주의사항
- .env → .gitignore 필수
- Slack 토큰 → 백엔드에서만 사용
- Supabase 동시접속 60개 → 쿼리 최적화
- Render 콜드스타트 → 10분마다 핑