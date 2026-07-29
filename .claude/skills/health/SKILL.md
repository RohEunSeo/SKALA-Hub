---
name: health
description: >
  프로젝트 전반의 코드 품질, 보안, 배포, 운영 상태를 점검하는 스킬.
  "헬스체크", "코드 점검해줘", "/health", "하드코딩 있어?", "보안 이슈 없어?",
  "배포 상태 괜찮아?", "운영 이슈 확인해줘" 등의 말이 나오면 이 스킬을 사용한다.
  Vue.js + Spring Boot + Supabase 기반 SKALA-Hub 프로젝트 구조 기준으로 점검한다.
---

# Project Health Check 스킬

`/health` 또는 "헬스체크해줘" 하면 코드를 직접 스캔해서 5가지 관점으로 점검한다.

---

## 점검 전 — 파일 구조 파악

먼저 프로젝트 구조를 확인한다:

```bash
# 프론트엔드
find src/ -name "*.vue" -o -name "*.js" | head -30

# 백엔드
find backend/src -name "*.java" | head -30

# 환경설정 파일
ls -la .env* backend/.env* 2>/dev/null
cat backend/build.gradle | grep -A5 "dotenv"
```

---

## 점검 항목 5가지

### 1. 🔐 보안 (Security)

```bash
# 하드코딩된 시크릿 탐지
grep -rn "xoxp-\|xoxb-\|sk-ant-\|eyJ" src/ backend/src/ --include="*.vue" --include="*.java" --include="*.js"

# API 키 직접 노출 여부
grep -rn "SUPABASE\|JWT_SECRET\|SLACK_TOKEN\|CLAUDE_API" src/ --include="*.vue" --include="*.js"

# .env 파일 .gitignore 등록 여부
cat .gitignore | grep ".env"
```

체크 항목:
- [ ] 하드코딩된 토큰/키 없음
- [ ] 프론트엔드 코드에 시크릿 없음
- [ ] `.env` 파일이 `.gitignore`에 포함됨
- [ ] JWT 인증이 필요한 API에 `@AuthenticationPrincipal` 또는 필터 적용됨

---

### 2. 🌐 API 설계 (Backend)

```bash
# 인증 없는 엔드포인트 확인
grep -rn "@GetMapping\|@PostMapping\|@DeleteMapping" backend/src/ --include="*.java" -A2

# SecurityConfig permitAll 범위 확인
grep -rn "permitAll\|antMatchers\|requestMatchers" backend/src/ --include="*.java"
```

체크 항목:
- [ ] `/api/posts/**` — JWT 인증 필수
- [ ] `/api/home/**` — public 허용 (의도적)
- [ ] `/api/health` — public 허용 (의도적)
- [ ] 불필요하게 public 열린 API 없음

---

### 3. ⚙️ 환경변수 & 배포 설정

```bash
# 환경변수 누락 여부 (실제 값 말고 키 이름만 확인)
cat backend/.env | grep -o "^[^=]*" | sort

# build.gradle dotenv 설정 확인
grep -A10 "dotenv\|env" backend/build.gradle

# Render 배포용 환경변수 목록과 대조
```

체크 항목:
- [ ] `backend/.env` 에만 환경변수 존재 (루트 `.env` 없거나 비어있음)
- [ ] 필수 환경변수 전부 있음: `JWT_SECRET`, `SLACK_USER_TOKEN`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `CLAUDE_API_KEY`
- [ ] `JWT_SECRET` 길이 32자 이상
- [ ] Render 환경변수와 로컬 `.env` 키 목록 일치 여부

---

### 4. 🖥️ 프론트엔드 코드 품질

```bash
# 하드코딩된 URL 탐지
grep -rn "localhost\|render.com\|vercel.app\|http://" src/ --include="*.vue" --include="*.js"

# API baseURL 설정 확인
cat src/api/axios.js 2>/dev/null || cat src/utils/axios.js 2>/dev/null || grep -rn "baseURL\|axios.create" src/

# 환경변수로 분리된 URL 확인
grep -rn "VITE_\|import.meta.env" src/ --include="*.vue" --include="*.js" | head -10
```

체크 항목:
- [ ] API URL이 `import.meta.env.VITE_API_URL` 등으로 분리됨
- [ ] `localhost` 하드코딩 없음
- [ ] 콘솔 로그 과도하게 남아있지 않음

```bash
# 과도한 console.log 확인
grep -rn "console.log" src/ --include="*.vue" --include="*.js" | wc -l
```

---

### 5. 🗄️ DB & 쿼리

```bash
# N+1 가능성 있는 쿼리 탐지 (루프 안에서 DB 호출)
grep -rn "repository\.\|findBy\|save(" backend/src/ --include="*.java" -B3 | grep -A3 "for\|stream\|forEach"

# HikariCP 커넥션 풀 설정 확인
grep -rn "maximum-pool-size\|connectionTimeout\|hikari" backend/src/main/resources/
```

체크 항목:
- [ ] 루프 안 DB 호출 없음 (N+1 쿼리)
- [ ] HikariCP 최대 커넥션 5 이하 (Supabase 무료 플랜 제한)
- [ ] Transaction mode 포트 6543 사용 중

---

## 출력 형식

```
🏥 SKALA-Hub 헬스체크 결과
━━━━━━━━━━━━━━━━━━━━━━━━━

🔐 보안         ✅ 이상 없음
🌐 API 설계     ⚠️  /api/replies 인증 누락 가능성
⚙️  환경변수     ✅ 이상 없음
🖥️  프론트엔드   ⚠️  console.log 12개 남아있음
🗄️  DB/쿼리     ✅ 이상 없음

━━━━━━━━━━━━━━━━━━━━━━━━━
🚨 즉시 수정 필요 (1개)
  → /api/replies GET 엔드포인트에 JWT 인증 없음
    SecurityConfig에 해당 경로 추가 필요

⚠️  권장 수정 (1개)
  → 배포 전 console.log 정리 또는 개발 환경에서만 출력되도록 처리

✅ 전반적으로 운영 상태 양호
```

---

## 규칙

- 파일을 **직접 읽어서** 판단한다 (추측 금지)
- 발견된 이슈는 **파일명 + 라인 번호**까지 명시
- 🚨 즉시 수정 / ⚠️ 권장 / ✅ 정상 3단계로만 표시
- 이상 없는 항목은 간단히 "✅ 이상 없음"으로 끝냄 (장황하게 쓰지 않음)
- 수정 방법은 이슈 바로 아래에 한 줄로 제시
