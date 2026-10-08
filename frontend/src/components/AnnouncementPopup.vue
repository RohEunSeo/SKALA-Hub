<script setup>
// 안 읽은 공지가 있으면 한 번 띄우는 팝업.
//
// 알림벨 배지만으로는 놓친다 - 챗봇 런처가 호버해야만 보여서 아무도 못 찾았던 것과 같은 문제다.
// 기능을 새로 열 때처럼 꼭 알려야 하는 공지가 묻히면 안 된다.
// 한 번 닫으면 그 공지는 다시 안 뜬다(서버 읽음 처리 + 브라우저 기록 둘 다).
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNotificationsStore } from '../stores/notifications'

const SEEN_KEY = 'skala-announce-seen'

const router = useRouter()
const authStore = useAuthStore()
const notificationsStore = useNotificationsStore()

const closed = ref(false)

/** 저장소가 막힌 환경(시크릿 창 등)에서도 화면은 정상 동작해야 한다. */
function seenIds() {
  try { return new Set(JSON.parse(localStorage.getItem(SEEN_KEY)) ?? []) } catch { return new Set() }
}
function remember(id) {
  try {
    const next = [...seenIds(), id].slice(-30) // 최근 것만 들고 있으면 충분하다
    localStorage.setItem(SEEN_KEY, JSON.stringify(next))
  } catch { /* 무시 */ }
}

// 안 읽은 공지 중 가장 최근 것 하나만. 여러 개를 쌓아 보여주면 아무것도 안 읽는다.
const item = computed(() => {
  if (closed.value || !authStore.isAuthenticated) return null
  const seen = seenIds()
  return notificationsStore.announcements.find((a) => !a.isRead && !seen.has(a.id)) ?? null
})

function close() {
  const id = item.value?.id
  closed.value = true
  if (id) remember(id)
  notificationsStore.markAllAnnouncementsRead()
}

function go(path) {
  close()
  router.push(path)
}

// 로그인하면 공지를 불러온다 (벨이 열릴 때까지 기다리면 팝업이 못 뜬다)
watch(
  () => authStore.isAuthenticated,
  (yes) => { if (yes) notificationsStore.loadAll() },
  { immediate: true },
)
</script>

<template>
  <Transition name="ann">
    <div v-if="item" class="ann-dim" role="dialog" aria-modal="true" :aria-label="item.title" @click.self="close">
      <div class="ann">
        <button class="ann-x" aria-label="닫기" @click="close">✕</button>
        <span class="ann-badge">{{ item.badgeType }}</span>
        <h2 class="ann-title">{{ item.title }}</h2>
        <p class="ann-body">{{ item.content }}</p>
        <div class="ann-foot">
          <button v-if="item.linkPath" class="ann-go" @click="go(item.linkPath)">
            {{ item.linkLabel || '바로가기' }} →
          </button>
          <button v-if="item.linkPath2" class="ann-go ghost" @click="go(item.linkPath2)">
            {{ item.linkLabel2 || '바로가기' }} →
          </button>
          <button class="ann-ok" @click="close">확인</button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.ann-dim {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(26, 26, 46, 0.42);
}

.ann {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: 26px 24px 20px;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(26, 26, 46, 0.24);
}

.ann-x {
  position: absolute;
  top: 14px;
  right: 16px;
  padding: 2px 4px;
  border: 0;
  background: none;
  color: #b2bec3;
  font-size: 14px;
  cursor: pointer;
}
.ann-x:hover { color: #636e72; }

.ann-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: #ece9fb;
  color: #4a3f8f;
  font-size: 11.5px;
  font-weight: 700;
}

.ann-title {
  margin: 10px 0 8px;
  color: #1a1a2e;
  font-size: 18px;
  line-height: 1.4;
}

.ann-body {
  margin: 0;
  color: #636e72;
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-line;   /* 공지 본문의 줄바꿈을 그대로 살린다 */
}

.ann-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.ann-go {
  padding: 9px 14px;
  border: 0;
  border-radius: 999px;
  background: #4a3f8f;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.ann-go.ghost { background: #ece9fb; color: #4a3f8f; }

.ann-ok {
  margin-left: auto;
  padding: 9px 16px;
  border: 1px solid #e0dcf4;
  border-radius: 999px;
  background: #ffffff;
  color: #636e72;
  font-size: 13px;
  cursor: pointer;
}
.ann-ok:hover { background: #f7f5fd; }

.ann-enter-active, .ann-leave-active { transition: opacity 0.22s ease; }
.ann-enter-from, .ann-leave-to { opacity: 0; }
.ann-enter-active .ann { transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1); }
.ann-enter-from .ann { transform: translateY(12px); }

@media (prefers-reduced-motion: reduce) {
  .ann-enter-active, .ann-leave-active, .ann-enter-active .ann { transition: none; }
}
</style>
