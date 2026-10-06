#!/usr/bin/env bash
# 루트 .env에서 AI 서버가 쓰는 키만 추려 Cloud Run용 yaml을 만든다.
#
# 통째로 올리지 않는 이유: 루트 .env에는 슬랙 토큰과 Supabase service_role 키가 있고
# AI 서버는 그걸 쓰지 않는다. 안 쓰는 비밀값을 GCP에 복사해둘 이유가 없다.
# (macOS 기본 bash 3.2에서도 돌아가게 연관배열을 쓰지 않는다)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/deploy/ai.env.yaml"

# AI 서버가 실제로 읽는 키만 (ai/app/config.py 기준)
KEYS="
  GEMINI_API_KEY OPENAI_API_KEY JWT_SECRET
  SUPABASE_URL SUPABASE_DB_HOST SUPABASE_DB_PORT SUPABASE_DB_USER SUPABASE_DB_PASSWORD
  LLM_PROVIDER LLM_MODEL LLM_FALLBACKS LLM_RETRIES EMBEDDING_MODEL
  VECTOR_STORE INDEX_VERSION
  CHAT_DAILY_LIMIT CHAT_ALLOWED_CAMPUS CHAT_ALLOWED_CLASSES CHAT_ALLOWED_USERS
  FRONTEND_URL CORS_ORIGINS
"

: > "$OUT"
chmod 600 "$OUT"
missing=""
for k in $KEYS; do
  # 마지막 정의를 쓴다. 따옴표와 끝 공백 제거
  v=$({ grep -E "^${k}=" "$ROOT/.env" || true; } | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//" -e 's/[[:space:]]*$//')

  # 운영에서 반드시 고정해야 하는 값은 .env의 로컬 설정을 덮어쓴다
  [ "$k" = "VECTOR_STORE" ] && v="pgvector"

  if [ -z "$v" ]; then missing="$missing $k"; continue; fi
  # Cloud Run은 환경변수를 전부 문자열로 받는다. 작은따옴표 안의 '는 ''로 escape
  printf "%s: '%s'\n" "$k" "$(printf '%s' "$v" | sed "s/'/''/g")" >> "$OUT"
done

echo "생성: $OUT  (키 $(wc -l < "$OUT" | tr -d ' ')개)"
[ -n "$missing" ] && echo "비어서 제외됨:$missing  ← config.py 기본값이 쓰인다"
: # set -e 에서 위 && 가 거짓이어도 죽지 않게
echo
echo "올라가는 키 (값은 안 보임):"
cut -d: -f1 "$OUT" | sed 's/^/  /'
