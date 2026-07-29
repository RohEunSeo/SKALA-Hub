---
name: git-commit
description: >
  Git 커밋 & 푸시를 반자동으로 처리하는 스킬. 사용자가 "커밋해줘", "이제 커밋할게",
  "/commit", "push해줘", "작업 마무리할게" 등의 말을 하면 반드시 이 스킬을 사용한다.
  변경된 파일 목록 확인 → 커밋 메시지 추천 → 사용자 승인 → add/commit/push 실행까지
  한 번에 처리한다. 코딩 세션이 마무리되는 느낌일 때도 선제적으로 제안한다.
---

# Git Commit 자동화 스킬

사용자가 작업을 마무리하려 할 때, 변경 파일 요약 → 커밋 메시지 추천 → 승인 후 실행까지 한 번에 처리한다.

---

## Step 1. 현재 상태 파악

아래 명령어를 순서대로 실행한다:

```bash
# 현재 브랜치 확인
git branch --show-current

# 변경된 파일 목록
git status --short

# 변경 내용 요약 (staged + unstaged 모두)
git diff --stat HEAD
```

---

## Step 2. 사용자에게 요약 보고

아래 형식으로 깔끔하게 보여준다:

```
📍 현재 브랜치: dev

📝 변경된 파일 (3개)
  M  src/views/PostsView.vue       ← 카테고리 변경 시 스크롤 초기화
  M  src/stores/postsStore.js      ← 정렬 옵션 상태 추가
  M  backend/PostsController.java  ← 정렬 파라미터 API 처리

💬 추천 커밋 메시지:
  "feat: 카테고리 변경 시 스크롤 상단 이동 및 정렬 기능 추가"

  다른 옵션:
  - "feat: 게시글 정렬(최신/인기/오래된순) 및 스크롤 초기화 구현"
  - "fix: 카테고리 탭 전환 시 스크롤 고정 버그 수정 + 정렬 기능"

👉 이 내용으로 커밋하고 push할까요? (수정하고 싶은 메시지 있으면 알려주세요)
```

---

## Step 3. 커밋 메시지 규칙

변경 내용을 분석해서 아래 prefix를 자동 선택한다:

| prefix | 언제 |
|--------|------|
| `feat:` | 새 기능 추가 |
| `fix:` | 버그 수정 |
| `refactor:` | 기능 변화 없는 코드 개선 |
| `style:` | UI/CSS 변경 |
| `chore:` | 설정, 환경변수, 패키지 등 |
| `docs:` | 주석, README 등 |

메시지는 **한국어**로 작성한다. (사용자가 영어 원하면 영어로)

---

## Step 4. 승인 받으면 실행

사용자가 "응", "ㅇㅇ", "해줘", "go" 등 승인하면:

```bash
# 변경 파일 전체 add
git add .

# 커밋 (확정된 메시지로)
git commit -m "feat: 카테고리 변경 시 스크롤 상단 이동 및 정렬 기능 추가"

# 현재 브랜치로 push
git push origin $(git branch --show-current)
```

실행 후 결과를 보여준다:
```
✅ 커밋 완료!
   브랜치: dev
   메시지: feat: 카테고리 변경 시 스크롤 상단 이동 및 정렬 기능 추가
   Push: origin/dev ✓
```

---

## 예외 처리

- **충돌 있을 때**: push 전에 경고하고 `git pull --rebase` 먼저 제안
- **특정 파일만 커밋하고 싶을 때**: 사용자가 "이 파일만" 하면 해당 파일만 `git add`
- **메시지 수정 원할 때**: 사용자가 직접 말해준 메시지로 교체
- **브랜치 확인 필요할 때**: main/master에 직접 push 하려 하면 "main에 바로 push해도 괜찮아요?" 한 번 확인
