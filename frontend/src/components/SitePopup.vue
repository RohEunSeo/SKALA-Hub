<script setup>
// 로그인한 사용자에게만 뜨는 일회성 공지 팝업 - 내용은 config/sitePopup.js에서 관리
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { SITE_POPUP } from '../config/sitePopup'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()

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
// '/feed' 처럼 내부 경로면 라우터로, 'https://...' 면 새 탭으로
const isInternal = SITE_POPUP.ctaUrl.startsWith('/')

/** 내부 경로면 그 화면으로 보내고, openChat 이면 조금 뒤에 챗봇을 연다.
 *  바로 열면 이미 열린 채로 도착해 '뭐가 열렸는지' 안 보인다 -
 *  피드가 그려진 뒤 패널이 미끄러져 나와야 어디를 보면 되는지 알 수 있다. */
const OPEN_CHAT_AFTER = 700

async function goCta() {
  close()
  if (!isInternal) return
  await router.push(SITE_POPUP.ctaUrl)
  if (SITE_POPUP.openChat) setTimeout(() => chatStore.open(), OPEN_CHAT_AFTER)
}

function isDismissed() {
  const raw = localStorage.getItem(SITE_POPUP.storageKey)
  if (!raw) return false
  const dismissedUntil = Number(raw)
  return !Number.isNaN(dismissedUntil) && Date.now() < dismissedUntil
}

function close() {
  visible.value = false
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
              <!-- 아이콘은 캠페인마다 다르다. 구글 로고를 박아두면 다음 공지에서 어색해진다 -->
              <span v-if="SITE_POPUP.featureIcon" class="popup-feature-icon" aria-hidden="true">{{ SITE_POPUP.featureIcon }}</span>
              {{ SITE_POPUP.featureTitle }}
            </div>
            <p class="popup-text">{{ SITE_POPUP.featureDescription }}</p>
          </div>

          <p class="popup-text popup-survey-note">
            <span v-for="(part, index) in surveyNoteParts" :key="index" :class="{ 'popup-bold': part.bold }">{{
              part.text
            }}</span>
          </p>

          <!-- 같은 사이트 안이면 라우터로 이동한다. 새 탭으로 열면 "나가는 것"처럼 느껴진다 -->
          <button v-if="isInternal" type="button" class="popup-cta" @click="goCta">{{
            SITE_POPUP.ctaLabel
          }}</button>
          <a v-else class="popup-cta" :href="SITE_POPUP.ctaUrl" target="_blank" rel="noopener noreferrer">{{
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

.popup-feature-icon {
  font-size: 15px;
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
