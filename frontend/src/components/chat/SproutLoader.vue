<script setup>
// AI 챗봇 "생각 중" 로더 - 새싹이 자라는 모션 (답변을 기다릴 때 사용). SVG + CSS만 사용, size로 크기 조절
defineProps({ size: { type: Number, default: 28 } })
</script>

<template>
  <span class="sprout-loader" :style="{ '--size': `${size}px` }" role="img" aria-label="불러오는 중">
    <svg viewBox="0 0 48 48" aria-hidden="true">
      <g class="sl-all">
        <ellipse class="sl-soil" cx="24" cy="44.5" rx="10" ry="2.2" />
        <path class="sl-stem" pathLength="1" d="M24 44 C24.5 37 24.5 31 23.5 24" />
        <path class="sl-l" d="M23.5 25 C14 25.5 8.5 18 9 9.5 C17.5 9.5 23.5 15 23.5 25 Z" />
        <path class="sl-r" d="M24 30.5 C31 30.5 38 25.5 39.5 17.5 C31.5 17.5 25 22 24 30.5 Z" />
      </g>
    </svg>
  </span>
</template>

<style scoped>
.sprout-loader {
  display: inline-block;
  width: var(--size);
  height: var(--size);
  flex: none;
}

.sprout-loader svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.sl-all {
  transform-origin: 24px 44px;
  animation: sl-all 2.2s cubic-bezier(0.45, 0, 0.55, 1) infinite;
}

.sl-stem {
  stroke: #43a047;
  stroke-width: 3.4;
  stroke-linecap: round;
  fill: none;
  stroke-dasharray: 1;
  animation: sl-stem 2.2s cubic-bezier(0.3, 0, 0.2, 1) infinite;
}

.sl-l {
  fill: #66bb6a;
  transform-origin: 23.5px 25px;
  animation: sl-l 2.2s cubic-bezier(0.34, 1.56, 0.64, 1) infinite;
}

.sl-r {
  fill: #5ba55e;
  transform-origin: 24px 30px;
  animation: sl-r 2.2s cubic-bezier(0.34, 1.56, 0.64, 1) infinite;
}

.sl-soil {
  fill: #b2a9e3;
  opacity: 0.55;
}

@keyframes sl-stem {
  0% { stroke-dashoffset: 1; }
  32%, 100% { stroke-dashoffset: 0; }
}

@keyframes sl-l {
  0%, 26% { transform: scale(0) rotate(25deg); }
  46%, 100% { transform: scale(1) rotate(0); }
}

@keyframes sl-r {
  0%, 38% { transform: scale(0) rotate(-25deg); }
  58%, 100% { transform: scale(1) rotate(0); }
}

@keyframes sl-all {
  0% { transform: scale(1); opacity: 1; }
  66% { transform: rotate(0); }
  74% { transform: rotate(-5deg); }
  82% { transform: rotate(3deg); opacity: 1; }
  96% { transform: scale(0.85); opacity: 0; }
  100% { transform: scale(1); opacity: 0; }
}

/* 동작 줄이기 설정을 켠 사용자는 완성된 새싹을 정지 상태로 보여줌 */
@media (prefers-reduced-motion: reduce) {
  .sl-all, .sl-stem, .sl-l, .sl-r { animation: none; }
  .sl-stem { stroke-dashoffset: 0; }
}
</style>
