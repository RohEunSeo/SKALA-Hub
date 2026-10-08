<script setup>
// 피드 우하단 AI 챗봇 런처 - 새싹이 든 폴더 모양 플로팅 버튼 (호버하면 폴더가 살짝 열리고 새싹이 까딱)
//
// 인사 말풍선: 호버 라벨(.fab-label)은 마우스를 올려야만 보이고 모바일에선 아예 숨겨져서,
// 챗봇이 있다는 걸 아무도 모른다. 그래서 늘 떠 있는 말풍선을 따로 둔다.
// 저절로 사라지지 않는다 - 사용자가 ✕로 닫을 때만 꺼지고, 그 뒤엔 호버 라벨이 대신한다.
import { ref } from 'vue'
import { useChatStore } from '../../stores/chat'

const chatStore = useChatStore()

const DISMISS_KEY = 'skala-chat-intro-dismissed'

/** 저장소가 막힌 환경(시크릿 창 등)에서도 화면은 정상 동작해야 한다. */
function dismissed() {
  try { return localStorage.getItem(DISMISS_KEY) === '1' } catch { return false }
}

const showIntro = ref(!dismissed())

function close() {
  showIntro.value = false
  try { localStorage.setItem(DISMISS_KEY, '1') } catch { /* 무시 */ }
}
</script>

<template>
  <!-- 인사 말풍선. 버튼 안에 닫기 버튼을 넣을 수 없어서 런처의 형제로 둔다 -->
  <Transition name="intro">
    <div v-if="showIntro && !chatStore.isOpen" class="fab-intro" role="status">
      <button class="fab-intro-close" aria-label="소개 닫기" @click="close">✕</button>
      <p class="fab-intro-title">저는 Hub 챗봇이에요 👋</p>
      <p class="fab-intro-body">찾는 주제를 말해주시면 관련된 글을 모아 드려요</p>
    </div>
  </Transition>

  <!-- 패널이 열려 있는 동안은 숨김 (패널 안의 X로 닫음) -->
  <button
    v-show="!chatStore.isOpen"
    class="chat-launcher"
    :class="{ 'has-intro': showIntro }"
    aria-label="AI 도우미 열기"
    @click="chatStore.open"
  >
    <span class="fab-tab"></span>
    <span class="fab-back"></span>
    <span class="fab-paper"></span>
    <span class="fab-front">
      <svg class="fab-sprout" viewBox="0 0 48 48" aria-hidden="true">
        <path d="M24 44 C24.5 37 24.5 31 23.5 24" fill="none" stroke="#43a047" stroke-width="3.4" stroke-linecap="round" />
        <path d="M23.5 25 C14 25.5 8.5 18 9 9.5 C17.5 9.5 23.5 15 23.5 25 Z" fill="#66bb6a" />
        <path d="M24 30.5 C31 30.5 38 25.5 39.5 17.5 C31.5 17.5 25 22 24 30.5 Z" fill="#5ba55e" />
      </svg>
    </span>
    <span v-if="chatStore.savedCount" class="fab-badge">{{ chatStore.savedCount }}</span>
    <span class="fab-label">AI에게 물어보기</span>
  </button>
</template>

<style scoped>
.chat-launcher {
  position: fixed;
  right: 20px;
  bottom: 44px;
  z-index: 60;
  width: 62px;
  /* 말풍선과 같은 주기로 움직여야 둘이 붙어 다니는 것처럼 보인다 */
  animation: fab-float 3.2s ease-in-out infinite;
  height: 50px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  perspective: 160px;
  filter: drop-shadow(0 6px 12px rgba(72, 64, 138, 0.28));
}

.chat-launcher > span {
  position: absolute;
  display: block;
}

.fab-tab {
  left: 0;
  top: 0;
  width: 42%;
  height: 24%;
  border-radius: 6px 6px 0 0;
  background: #b2a9e3;
}

.fab-back {
  left: 0;
  right: 0;
  top: 12%;
  bottom: 0;
  border-radius: 8px;
  background: #b2a9e3;
}

/* 말풍선 꼬리. '대화하는 폴더'로 읽히게 하는 유일한 장치라 폴더 모양은 그대로 둔다.
   호버하면 기울어지는 앞면(.fab-front) 대신 고정된 뒷면에 달아야 같이 휘지 않는다.
   색은 앞면 아래쪽 그라데이션 끝값과 맞춘다. */
.fab-back::after {
  content: '';
  position: absolute;
  right: 7px;
  bottom: -7px;
  width: 0;
  height: 0;
  border-top: 10px solid #b9b1e3;
  border-left: 11px solid transparent;
}

.fab-paper {
  left: 8%;
  right: 8%;
  top: 17%;
  height: 46%;
  border-radius: 4px;
  background: #ffffff;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.fab-front {
  left: 0;
  right: 0;
  top: 26%;
  bottom: 0;
  border-radius: 8px;
  transform-origin: 50% 100%;
  background: linear-gradient(#cfc9f3, #b9b1e3);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition: transform 0.32s cubic-bezier(0.34, 1.56, 0.64, 1);
  display: grid;
  place-items: end end;
  padding: 0 6px 5px 0;
  box-sizing: border-box;
}

.fab-sprout {
  width: 17px;
  height: 17px;
  transform-origin: 50% 100%;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.chat-launcher:hover .fab-front,
.chat-launcher:focus-visible .fab-front {
  transform: rotateX(-22deg);
}

.chat-launcher:hover .fab-paper,
.chat-launcher:focus-visible .fab-paper {
  transform: translateY(-3px);
}

.chat-launcher:hover .fab-sprout {
  transform: rotate(-8deg) scale(1.12);
}

.chat-launcher:focus-visible {
  outline: 2px solid #6c5ce7;
  outline-offset: 4px;
  border-radius: 8px;
}

/* 챗봇으로 저장한 글 수 배지 */
.fab-badge {
  top: -8px;
  right: -8px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  background: #4a3f8f;
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
  border: 2px solid #fafafa;
  box-sizing: content-box;
}

/* 호버하면 왼쪽으로 라벨이 펼쳐짐 */
.fab-label {
  right: 72px;
  top: 50%;
  transform: translateY(-50%) translateX(8px);
  padding: 6px 12px;
  border-radius: 999px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.12);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease, transform 0.25s ease;
}

.chat-launcher:hover .fab-label {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

/* 모바일은 호버가 없으니 라벨 생략 */
@media (max-width: 900px) {
  .fab-label {
    display: none;
  }
}

/* 말풍선이 떠 있는 동안은 호버 라벨을 숨긴다 (같은 자리라 겹친다) */
.chat-launcher.has-intro:hover .fab-label {
  opacity: 0;
}

/* ── 인사 말풍선 ───────────────────────────────────────────── */
.fab-intro {
  position: fixed;
  right: 40px;          /* 런처(right 20, 폭 62)보다 왼쪽으로 */
  bottom: 104px;        /* 런처 윗변(bottom 94)보다 위로 */
  animation: fab-float 3.2s ease-in-out infinite;
  z-index: 60;
  max-width: 360px;
  padding: 12px 30px 12px 14px;
  border-radius: 14px;
  background: #f1eefc;
  box-shadow: 0 6px 18px rgba(74, 63, 143, 0.18);
  text-align: left;
}

/* 런처를 가리키는 꼬리 */
.fab-intro::after {
  content: '';
  position: absolute;
  right: 20px;
  bottom: -5px;
  width: 10px;
  height: 10px;
  background: #f1eefc;
  transform: rotate(45deg);
  box-shadow: 2px 2px 4px rgba(74, 63, 143, 0.08);
}

.fab-intro-title {
  margin: 0 0 4px;
  color: #4a3f8f;
  font-size: 14px;
  font-weight: 700;
}

.fab-intro-body {
  margin: 0;
  color: #5b5570;
  font-size: 12.5px;
  line-height: 1.5;
  white-space: nowrap;   /* 한 줄로 유지 (좁은 화면에서는 아래에서 다시 푼다) */
}

.fab-intro-close {
  position: absolute;
  top: 6px;
  right: 8px;
  padding: 2px 4px;
  border: 0;
  background: none;
  color: #9a93b5;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}

.fab-intro-close:hover {
  color: #4a3f8f;
}

.intro-enter-active,
.intro-leave-active {
  transition: opacity 0.25s ease;
}

.intro-enter-from,
.intro-leave-to {
  opacity: 0;
}

/* 가만히 있으면 눈에 안 들어와서 아주 느리게 위아래로 뜬다 */
@keyframes fab-float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-6px); }
}

/* 좁은 화면에서는 옆에 둘 자리가 없어 런처 위로 올린다 */
@media (max-width: 900px) {
  .fab-intro {
    right: 20px;
    bottom: 104px;
    max-width: calc(100vw - 40px);
  }
  .fab-intro-body {
    white-space: normal;   /* 화면이 좁으면 두 줄이 되는 게 낫다 */
  }

}

@media (prefers-reduced-motion: reduce) {
  .fab-front, .fab-paper, .fab-sprout, .fab-label { transition: none; }
  .intro-enter-active, .intro-leave-active { transition: none; }
  .chat-launcher, .fab-intro { animation: none; }
}
</style>
