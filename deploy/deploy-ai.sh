#!/usr/bin/env bash
# AI 서버(ai/)를 Google Cloud Run에 배포한다.
#
# 처음 한 번만 해야 하는 것(이 스크립트 밖):
#   brew install --cask google-cloud-sdk
#   gcloud auth login
#   gcloud config set project <PROJECT_ID>
#   gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVICE="${SERVICE:-skala-hub-ai}"
# 서울. Supabase가 ap-northeast-2(서울)라 DB 왕복을 줄인다
REGION="${REGION:-asia-northeast3}"

# 환경변수 파일을 매번 .env에서 새로 만든다 - 손으로 고친 값이 뒤처지지 않게
"$ROOT/deploy/make-env.sh"
echo

gcloud run deploy "$SERVICE" \
  --source "$ROOT/ai" \
  --region "$REGION" \
  --env-vars-file "$ROOT/deploy/ai.env.yaml" \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 3 \
  --concurrency 20 \
  --timeout 120s \
  --cpu-boost

# --allow-unauthenticated: 인증은 Spring이 발급한 JWT를 서버가 직접 검증한다(app/routers/chat.py).
#   GCP IAM 인증을 켜면 브라우저가 구글 토큰을 따로 받아야 해서 두 겹이 된다.
# --min-instances 0: 상시 가동은 무료 할당(월 18만 vCPU-초)을 한참 넘긴다.
#   콜드스타트는 임포트 합계 0.7초로 측정됐고 --cpu-boost로 더 줄인다.
# --max-instances 3: 폭주 시 요금 상한. 27명 x 동시성 20 = 60명 동시도 감당한다.
# --timeout 120s: SSE라 응답 동안 연결을 붙잡는다. 현재 가장 느린 답변이 20초 안쪽.

echo
echo "=== 배포 주소 ==="
URL=$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')
echo "  $URL"
echo
echo "다음: Vercel 환경변수에 넣어야 챗봇이 켜진다 (없으면 자동으로 숨겨짐)"
echo "  VITE_AI_API_BASE_URL=$URL"
echo
echo "헬스체크:"
echo "  curl $URL/health"
