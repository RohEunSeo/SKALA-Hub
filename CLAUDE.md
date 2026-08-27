# SKALA Hub

## 서비스
SKALA 부트캠프 교육생(340명, 5개월) 슬랙 정보공유 채널 아카이빙 플랫폼.
1인 바이브코딩 → 코드 단순하게, 핵심 한국어 주석, 환경변수 하드코딩 금지.

## 기술 스택
- 프론트: Vue.js 3 (Vite + Pinia + Vue Router 4) → Vercel 배포
- 백엔드: Spring Boot 4 (Java 21) → Render 배포
- DB: Supabase (PostgreSQL)
- AI 분류: Claude Haiku API

## 환경변수 (.env)

SLACK_CLIENT_ID=
SLACK_CLIENT_SECRET=
SLACK_USER_TOKEN=
SLACK_BOT_TOKEN=
SLACK_CHANNEL_ID=C0BHGGH7PT3
SLACK_GWANGJU_CHANNEL_ID=
SLACK_SIGNING_SECRET=
SLACK_ADMIN_DM_USER_ID=
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
SUPABASE_DB_PASSWORD=
CLAUDE_API_KEY=
OPENWEATHER_API_KEY=
JWT_SECRET=
JWT_EXPIRATION=86400000
FRONTEND_URL=


## DB 구조 (Supabase, 마이그레이션 SQL은 backend/*.sql)

users → 교육생 계정 (slack_id PK, name, cohort, campus, class_num, role, profile_img)
posts → 슬랙 게시글 (slack_ts UK, user_name, is_instructor, content, category, tags[], reaction_count, reply_count, is_deleted, is_pinned, is_excluded_from_ranking)
replies → 스레드 댓글 (post_id FK → posts)
bookmarks → 저장하기 (user_id FK → users, post_id FK → posts, UNIQUE 조합)
link_previews → 링크 모음 카드용 메타데이터 (제목/출처/이모지/admin_creators 등 관리자 수동 보정값)
announcements / announcement_reads → 관리자 전체 공지 + 유저별 읽음 추적
notifications → 개인 알림 (BOOKMARK_RECEIVED, WEEKLY_TOP3 - 백엔드 자동 발생만 존재, 관리자 수동 작성 없음)


## 카테고리 (frontend/src/constants/categories.js가 원본)

상위 6개: 개발 툴·환경 / 학습 자료 / 자격증·취업 / 교육생 서비스 / 교수님 게시글 / 기타
- 학습 자료 하위태그: 영상 / 블로그·글 / 깃허브·코드
- 교육생 서비스 하위태그: 캠퍼스 생활 편의 / SKCT·SQLD / 학습 및 스터디매칭 / 개발 생산성 및 툴 / 기타 (+ 관리자 전용 태그: 사이트 / 앱·툴 / Extension)
- 기타 하위태그: 인사이트·경험 공유 / 오류 해결 / 분실물 / 맛집 / 그 외
교수님 판단: user_name에 "교수" 또는 "전임" 포함 시 자동 분류
카테고리/태그를 추가하거나 이름을 바꿀 땐 categories.js 한 곳만 고치면 됨 (Sidebar/CategoryFilter/Home 공용).


## Slack API

Channel ID: C0BHGGH7PT3 (판교, private 채널 → groups:history 권한 필요), 광주 캠퍼스는 SLACK_GWANGJU_CHANNEL_ID로 별도 채널 연동
conversations.history → 전체 게시글 수집 (페이지네이션)
conversations.replies → 스레드 댓글 수집
users.info → 유저 이름 조회 (캐싱 필수)
chat.postMessage / chat.update / chat.delete → 관리자가 동기화 안내용 봇 댓글을 스레드에 남김 (SlackBotReplyService)
이미지 url_private → 백엔드 프록시로 제공
슬랙 딥링크 → https://theskala.slack.com/archives/C0BHGGH7PT3/p{ts}


## 게시글 표시 방식 (슬랙 원본 그대로)

본문 텍스트: 슬랙 원문 그대로 표시
본문 이모지: 슬랙 텍스트 내 이모지(:white_check_mark: 등) → 실제 이모지로 변환
반응 이모지: 개별 구분 없이 총 반응 수 합산만 표시
링크: 하늘색(#1264A3) 하이라이트 텍스트 + 클릭 가능
링크 미리보기: attachments 데이터로 카드 렌더링 (제목/설명/썸네일)
인라인 코드: 연한 빨간 배경(#F7E0D9) + 빨간 텍스트(#E01E5A) + 모노스페이스
코드 블록: 연한 회색 배경(#F8F8F8) + 테두리 + 모노스페이스 (슬랙 스타일 그대로)
이미지: 백엔드 프록시로 미리보기 표시 (gif 포함, 움직임 유지)
파일 첨부: 파일명 + 파일 형식 아이콘 표시, 하단에 "* 원본 파일은 슬랙에서 다운받아 주세요." 문구 표시
댓글: 댓글 수 표시 + 클릭하면 펼치기
슬랙에서 보기: 새 탭으로 해당 메시지 위치로 바로 이동


## 동기화 전략

게시글 저장/분류/봇 댓글은 전부 스케줄러/수동 방식 (SlackSyncService)
- 스케줄러: 5분마다 증분 동기화(SLACK_SYNC_INTERVAL_MS), 매일 새벽 4시 전체 재수집
- 관리자 수동: /admin에서 가벼운 동기화 / 전체 재수집 / 링크 미리보기 재수집 버튼 3종 직접 실행 가능
- Slack Events API(실시간 webhook)는 관리자 개인 DM 알림 전용으로만 사용 (SlackEventsController, POST /api/slack/events) -
  새 글이 올라오는 즉시 SLACK_ADMIN_DM_USER_ID로 "새 글 감지" DM 발송, 이후 스케줄러가 실제로 동기화/분류를 마치면
  성공/실패·전체 누적 순번·분류 카테고리를 담은 DM을 한 번 더 발송(SlackDmNotificationService). 게시글 저장 자체는
  이 웹훅에서 하지 않고 여전히 스케줄러 경로로만 이루어짐


## 컬러 시스템

메인: #4A3F8F
서브: #6C5CE7
배경: #FAFAFA
카드: #FFFFFF
텍스트: #1A1A2E
보조텍스트: #636E72
링크: #1264A3 (슬랙 링크 색상)
인라인코드: #F7E0D9 배경 / #E01E5A 텍스트
코드블록: #F8F8F8 배경


## 디자인
`design/`은 gitignore 처리되어 로컬 전용이며 실제 UI보다 뒤처져 있음(참고용 아님).
새 화면/컴포넌트를 만들 땐 기존 Vue 컴포넌트(특히 AppLayout, Sidebar, PostCard)의 스타일·클래스 패턴을 그대로 따라가는 게 기준.


## 프로젝트 구조

skala-hub/
├── frontend/src/
│ ├── views/       # HomeView, FeedView, PostDetailView, MyPageView, AdminView
│ ├── components/  # PostCard, Sidebar, LinkGalleryCard, SortFilter, NotificationBell 등
│ ├── stores/       # auth, posts, bookmarks, home, mypage, notifications, toast, ui
│ ├── router/
│ └── api/
├── backend/src/main/java/com/skalahub/
│ ├── controller/   # Admin, AdminAnnouncement, Announcement, Auth, Bookmark, Home, Link, MyPage, Notification, Post, Health, FileProxy
│ ├── service/
│ ├── repository/
│ ├── entity/       # User, Post, Reply, Bookmark, LinkPreview, Announcement, AnnouncementRead, Notification, SyncFailure
│ └── config/       # Security, CORS 설정
├── backend/*.sql    # 수동 적용 마이그레이션 스크립트
├── design/          # gitignore (로컬 전용 목업, 최신 아님)
├── CLAUDE.md
├── .env            # 환경변수 (gitignore)
└── .gitignore


## 관리자 기능 (role=admin만 접근, /admin)

동기화: 가벼운 동기화 / 전체 재수집 / 링크 미리보기 재수집 + 동기화 실패 목록 확인
슬랙 봇 댓글 관리: 성공/실패/대기 필터, 댓글 수정·삭제, 보류분 수동 전송
미분류 게시글 목록 + 일괄 분류
게시글 카테고리/태그/핀 고정 수동 수정 (개별 저장 + 여러 건 일괄 저장)
순위보드 제외/복원 (홈 화면 순위보드에서 처리, 게시글 자체는 삭제 안 됨 - is_excluded_from_ranking)
링크 모음 카드 수정(제목/출처/만든사람/유형태그/이모지) + 숨김/복원
공지 관리: 등록/수정/삭제, 프리셋, 카테고리·태그 딥링크
관리자 전용 화면 요소는 authStore.effectiveIsAdmin 하나로 통제 (관리자가 상단 토글로 일반 모드 미리보기 가능)


## 주의사항

.env → .gitignore 필수 (토큰 노출 절대 금지)
Slack 토큰 → 백엔드에서만 사용, 프론트 노출 금지
Supabase 동시접속 60개 제한 → DB 쿼리 최적화
Render 무료 플랜 콜드스타트 → 10분마다 핑 전송
Slack Rate Limit 분당 50회 → DB 캐싱으로 해결
웹 브라우저 테스트는 제외할 것, 테스트해야할 목록만 알려주면 내가 직접 테스트
