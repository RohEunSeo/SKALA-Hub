<script setup>
// 로그인한 사용자에게만 뜨는 일회성 공지 팝업 - 내용은 config/sitePopup.js에서 관리
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { SITE_POPUP } from '../config/sitePopup'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

const visible = ref(false)
const panelRef = ref(null)

// [[ ]]로 감싼 부분은 빨간 글씨, ** **로 감싼 부분은 볼드로 렌더링 - 캡처 그룹이 있는 split이라
// 결과의 홀수 인덱스가 마커로 감싼 조각
function parseRich(text) {
  return text.split(/(\[\[.+?\]\]|\*\*.+?\*\*)/).map((piece) => {
    if (piece.startsWith('[[')) return { text: piece.slice(2, -2), red: true }
    if (piece.startsWith('**')) return { text: piece.slice(2, -2), bold: true }
    return { text: piece }
  })
}

const introParts = computed(() => parseRich(SITE_POPUP.intro))
const warningParts = computed(() => parseRich(SITE_POPUP.warning))
const surveyNoteParts = computed(() => parseRich(SITE_POPUP.surveyNote))

// 오늘 날짜(로컬 기준, YYYY-MM-DD) - "닫기"를 누른 날은 새로고침/재접속해도 다시 안 뜨고, 다음 날 첫 접속에 다시 뜸
function todayKey() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
const CLOSED_TODAY_KEY = `${SITE_POPUP.storageKey}:closedOn`

function isDismissed() {
  try {
    // "일주일 동안 보지 않음"
    const raw = localStorage.getItem(SITE_POPUP.storageKey)
    const dismissedUntil = raw ? Number(raw) : NaN
    if (!Number.isNaN(dismissedUntil) && Date.now() < dismissedUntil) return true
    // "닫기"(✕/ESC 포함)를 오늘 이미 눌렀는지
    return localStorage.getItem(CLOSED_TODAY_KEY) === todayKey()
  } catch {
    // localStorage 접근 불가(프라이빗 모드 등)면 기억할 수 없으므로 그냥 노출
    return false
  }
}

function close() {
  visible.value = false
  try {
    localStorage.setItem(CLOSED_TODAY_KEY, todayKey())
  } catch {
    // 저장 실패해도 이번 방문에서 닫히는 데는 문제 없음
  }
}

function dismissForDays() {
  const until = Date.now() + SITE_POPUP.dismissDays * 24 * 60 * 60 * 1000
  try {
    localStorage.setItem(SITE_POPUP.storageKey, String(until))
  } catch {
    // localStorage 접근 불가(프라이빗 모드 등)해도 이번 방문에서 닫히는 데는 문제 없음
  }
  close()
}

function handleKeydown(event) {
  if (event.key === 'Escape') close()
}

// 로그인 상태일 때만 노출 - 이미 로그인된 채 접속하면 바로, 비로그인 상태에서 로그인하면 로그인 직후 바로 뜸.
// 로그아웃하면 열려 있던 팝업도 닫음 ("일주일 동안 보지 않음"을 누른 경우엔 로그인해도 안 뜸)
watch(
  () => authStore.isAuthenticated,
  (loggedIn) => {
    const active = SITE_POPUP.enabled && Date.now() < new Date(SITE_POPUP.campaignEndsAt).getTime()
    visible.value = loggedIn && active && !isDismissed()
  },
  { immediate: true },
)

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="popup-overlay">
      <div ref="panelRef" class="popup-panel" role="dialog" aria-modal="true" :aria-label="SITE_POPUP.title">
        <button class="popup-close" aria-label="닫기" @click="close">✕</button>

        <div class="popup-body">
          <div class="popup-head">
            <h2 class="popup-heading">
              {{ SITE_POPUP.badge }} : {{ SITE_POPUP.title }}
            </h2>
            <p class="popup-intro">
              <span v-for="(part, index) in introParts" :key="index" :class="{ 'popup-bold': part.bold }">{{
                part.text
              }}</span>
            </p>
          </div>

          <p class="popup-warning">
            <span
              v-for="(part, index) in warningParts"
              :key="index"
              :class="{ 'popup-red': part.red, 'popup-bold': part.bold }"
              >{{ part.text }}</span
            >
          </p>

          <p class="popup-lead">{{ SITE_POPUP.lead }}</p>

          <div class="popup-feature">
            <div class="popup-feature-title">
              <svg class="popup-google-logo" viewBox="0 0 48 48" aria-hidden="true">
                <path
                  fill="#EA4335"
                  d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
                />
                <path
                  fill="#4285F4"
                  d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
                />
                <path
                  fill="#FBBC05"
                  d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
                />
                <path
                  fill="#34A853"
                  d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
                />
              </svg>
              {{ SITE_POPUP.featureTitle }}
            </div>
            <p class="popup-text">{{ SITE_POPUP.featureDescription }}</p>
          </div>

          <p class="popup-text popup-survey-note">
            <span v-for="(part, index) in surveyNoteParts" :key="index" :class="{ 'popup-bold': part.bold }">{{
              part.text
            }}</span>
          </p>

          <a class="popup-cta" :href="SITE_POPUP.ctaUrl" target="_blank" rel="noopener noreferrer">{{
            SITE_POPUP.ctaLabel
          }}</a>
        </div>

        <div class="popup-actions">
          <button class="popup-action-btn" @click="dismissForDays">일주일 동안 보지 않음</button>
          <button class="popup-action-btn" @click="close">닫기</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.popup-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 26, 46, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1100;
}

.popup-panel {
  position: relative;
  width: 100%;
  max-width: 480px;
  min-height: 520px;
  max-height: 90vh;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(26, 26, 46, 0.25);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.popup-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: none;
  background: rgba(26, 26, 46, 0.06);
  color: #1a1a2e;
  font-size: 14px;
  cursor: pointer;
}

.popup-close:hover {
  background: rgba(26, 26, 46, 0.12);
}

.popup-body {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 44px 22px 28px;
}

.popup-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.popup-intro {
  margin: 0;
  font-size: 12.5px;
  color: #8890a3;
}

.popup-heading {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.8;
  color: #1a1a2e;
  white-space: nowrap;
}

/* 좁은 화면에서는 제목이 잘리지 않도록 줄바꿈 허용 */
@media (max-width: 540px) {
  .popup-heading {
    white-space: normal;
  }
}

.popup-warning,
.popup-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: #1a1a2e;
  white-space: pre-line;
  word-break: keep-all;
}

.popup-red {
  color: #d63031;
  font-weight: 700;
}

.popup-bold {
  font-weight: 700;
}

.popup-lead {
  margin: 0;
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.7;
  color: #4a3f8f;
  white-space: pre-line;
  word-break: keep-all;
}

.popup-feature {
  padding: 16px;
  border-radius: 12px;
  background: #f1eefc;
}

.popup-feature-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 700;
  color: #4a3f8f;
  margin-bottom: 6px;
}

.popup-google-logo {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.popup-survey-note {
  color: #636e72;
}

.popup-cta {
  display: block;
  margin-top: 4px;
  padding: 14px;
  border-radius: 10px;
  background: #4a3f8f;
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  text-decoration: none;
}

.popup-cta:hover {
  background: #3d3475;
}

.popup-actions {
  display: flex;
  border-top: 1px solid #f0f0f4;
}

.popup-action-btn {
  flex: 1;
  padding: 14px;
  border: none;
  background: transparent;
  color: #636e72;
  font-size: 13px;
  cursor: pointer;
}

.popup-action-btn:first-child {
  border-right: 1px solid #f0f0f4;
}

.popup-action-btn:hover {
  background: #fafafa;
  color: #1a1a2e;
}
</style>
