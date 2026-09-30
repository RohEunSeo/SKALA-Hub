<script setup>
// 피드 우하단 AI 챗봇 런처 - 새싹이 든 폴더 모양 플로팅 버튼 (호버하면 폴더가 살짝 열리고 새싹이 까딱)
import { useChatStore } from '../../stores/chat'

const chatStore = useChatStore()
</script>

<template>
  <!-- 패널이 열려 있는 동안은 숨김 (패널 안의 X로 닫음) -->
  <button
    v-show="!chatStore.isOpen"
    class="chat-launcher"
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
  bottom: 24px;
  z-index: 60;
  width: 62px;
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

@media (prefers-reduced-motion: reduce) {
  .fab-front, .fab-paper, .fab-sprout, .fab-label { transition: none; }
}
</style>
