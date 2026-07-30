# SKALA Hub

## 서비스
SKALA 부트캠프 교육생(340명, 5개월) 슬랙 정보공유 채널 아카이빙 플랫폼.
1인 바이브코딩 → 코드 단순하게, 핵심 한국어 주석, 환경변수 하드코딩 금지.

## 기술 스택
- 프론트: Vue.js 3 (Vite + Pinia + Vue Router 4) → Vercel 배포
- 백엔드: Spring Boot 4 (Java 21) → Render 배포
- DB: Supabase (PostgreSQL) → 테이블 생성 완료
- AI 분류: Claude Haiku API

## 환경변수 (.env)

SLACK_CLIENT_ID=
SLACK_CLIENT_SECRET=
SLACK_USER_TOKEN=
SLACK_BOT_TOKEN=
SLACK_CHANNEL_ID=C0BHGGH7PT3
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
CLAUDE_API_KEY=
JWT_SECRET=
JWT_EXPIRATION=86400000
TEST_MODE=false


## DB 구조 (Supabase에 생성 완료)

users → 교육생 계정 (slack_id PK, name, cohort, campus, class_num, role, profile_img)
posts → 슬랙 게시글 (slack_ts UK, user_name, is_instructor, content, category, tags[], reaction_count, reply_count, is_deleted, is_pinned)
replies → 스레드 댓글 (post_id FK → posts)
bookmarks → 저장하기 (user_id FK → users, post_id FK → posts, UNIQUE 조합)


## 카테고리

상위 6개: 개발도구 / 학습자료 / 취업자격증 / 교육생서비스 / 교수님 / 기타
학습자료 하위태그: 영상 / 아티클 / 깃허브
교수님 판단: user_name에 "교수" 또는 "전임" 포함 시 자동 분류


## Slack API (검증 완료)

Channel ID: C0BHGGH7PT3 (private 채널 → groups:history 권한 필요)
conversations.history → 전체 게시글 수집 (페이지네이션)
conversations.replies → 스레드 댓글 수집
users.info → 유저 이름 조회 (캐싱 필수)
이미지 url_private → 백엔드 프록시로 제공
슬랙 딥링크 → https://theskala.slack.com/archives/C0BHGGH7PT3/p{ts}


## 게시글 표시 방식 (슬랙 원본 그대로)

본문 텍스트: 슬랙 원문 그대로 표시
본문 이모지: 슬랙 텍스트 내 이모지(:white_check_mark: 등) → 실제 이모지로 변환
반응 이모지: 개별 구분 없이 총 반응 수 합산만 표시
링크: 하늘색(
#1264A3) 하이라이트 텍스트 + 클릭 가능
링크 미리보기: attachments 데이터로 카드 렌더링 (제목/설명/썸네일)
인라인 코드: 연한 빨간 배경(
#F7E0D9) + 빨간 텍스트(
#E01E5A) + 모노스페이스
코드 블록: 연한 회색 배경(
#F8F8F8) + 테두리 + 모노스페이스 (슬랙 스타일 그대로)
이미지: 백엔드 프록시로 미리보기 표시 (gif 포함, 움직임 유지)
파일 첨부: 파일명 + 파일 형식 아이콘 표시
하단에 "* 원본 파일은 슬랙에서 다운받아 주세요." 문구 표시
댓글: 댓글 수 표시 + 클릭하면 펼치기
슬랙에서 보기: 새 탭으로 해당 메시지 위치로 바로 이동


## 동기화 전략

최초 1회: conversations.history 전체 수집 → DB 저장
이후 실시간: Slack Event API (새 글/수정/삭제 감지)
주기적: 30분마다 스케줄러 → 이모지/댓글 수 업데이트


## 컬러 시스템

메인: 
#4A3F8F
서브: 
#6C5CE7
배경: 
#FAFAFA
카드: 
#FFFFFF
텍스트: 
#1A1A2E
보조텍스트: 
#636E72
링크: 
#1264A3 (슬랙 링크 색상)
인라인코드: 
#F7E0D9 배경 / 
#E01E5A 텍스트
코드블록: 
#F8F8F8 배경


## 디자인
design/SKALA_Hub_standalone.html 이 UI 목업.
Vue 컴포넌트 만들 때 이 파일 HTML/CSS 그대로 참고해서 구현.

## 프로젝트 구조

skala-hub/
├── frontend/ # Vue.js 3
│ └── src/
│ ├── views/ # LoginView, HomeView, FeedView, MyPageView, AdminView
│ ├── components/ # PostCard, Sidebar, SearchBar, CategoryFilter
│ ├── stores/ # auth.js, posts.js, bookmarks.js
│ ├── router/ # index.js
│ └── api/ # auth.js, posts.js
├── backend/ # Spring Boot 4
│ └── src/main/java/com/skalahub/
│ ├── controller/ # API 엔드포인트
│ ├── service/ # 비즈니스 로직
│ ├── repository/ # DB 접근
│ ├── entity/ # DB 테이블 매핑
│ └── config/ # Security, CORS 설정
├── design/
│ └── SKALA_Hub_standalone.html
├── CLAUDE.md
├── .env # 환경변수 (gitignore)
└── .gitignore


## 관리자 기능 (role=admin만 접근)

/admin 페이지 별도 접근
게시글 카테고리/태그 수동 수정
게시글 핀 고정/해제
미분류 게시글 목록 + 일괄 분류
전체 동기화 버튼


## 추후 추가 예정 (Phase 9)
- 슬랙 관리자 알림 봇
  - 새 게시글 등록 / 분류 실패 / 서버 에러 / 일일 리포트
  - SLACK_BOT_TOKEN, SLACK_ADMIN_USER_ID 필요

## 주의사항

.env → .gitignore 필수 (토큰 노출 절대 금지)
Slack 토큰 → 백엔드에서만 사용, 프론트 노출 금지
Supabase 동시접속 60개 제한 → DB 쿼리 최적화
Render 무료 플랜 콜드스타트 → 10분마다 핑 전송
Slack Rate Limit 분당 50회 → DB 캐싱으로 해결