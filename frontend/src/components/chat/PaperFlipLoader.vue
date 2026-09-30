<script setup>
// AI 챗봇 "게시글 검색 중" 로더 - 폴더 속 종이를 넘겨보며 스캔하는 모션 (검색/필터 도구가 도는 동안 사용).
// CSS만 사용, width(px)로 크기 조절
// duration(초): 종이 3장이 한 바퀴 넘어가는 시간 - 기본 1.5초(검색 로더), 첫 화면 연출은 더 느리게
// still: 애니메이션 없이 문서가 폴더 위로 살짝 나와 있는 정지 상태 (추천 결과 옆 폴더용)
defineProps({ width: { type: Number, default: 48 }, duration: { type: Number, default: 1.5 }, still: Boolean })
</script>

<template>
  <span class="pf" :class="{ still }" :style="{ '--w': `${width}px`, '--d': `${duration}s` }" role="img" aria-label="검색 중">
    <span class="pf-tab"></span>
    <span class="pf-back"></span>
    <span class="pf-paper"></span>
    <span class="pf-paper"></span>
    <span class="pf-paper"></span>
    <span class="pf-front"></span>
  </span>
</template>

<style scoped>
.pf {
  position: relative;
  display: inline-block;
  width: var(--w);
  aspect-ratio: 48 / 40;
  flex: none;
}

.pf > span {
  position: absolute;
  display: block;
}

.pf-tab {
  left: 0;
  top: 0;
  width: 42%;
  height: 22%;
  border-radius: 12% 12% 0 0 / 30% 30% 0 0;
  background: #b2a9e3;
}

.pf-back {
  left: 0;
  right: 0;
  top: 10%;
  bottom: 0;
  border-radius: 10% / 12%;
  background: #b2a9e3;
}

.pf-paper {
  top: 16%;
  height: 56%;
  width: 70%;
  border-radius: 6% / 8%;
  background: #ffffff repeating-linear-gradient(to bottom, transparent 0 18%, #dcd7f2 18% 26%) 20% 30% / 60% 60% no-repeat;
  box-shadow: 0 1px 2px rgba(72, 64, 138, 0.18);
  transform-origin: 50% 100%;
  animation: pf-rise var(--d) cubic-bezier(0.45, 0, 0.3, 1) infinite;
}

/* 종이 위를 훑는 보라색 스캔 줄 */
.pf-paper::after {
  content: '';
  position: absolute;
  left: 12%;
  right: 12%;
  height: 8%;
  top: 20%;
  border-radius: 99px;
  background: #8b7ee8;
  opacity: 0;
  animation: pf-scan var(--d) linear infinite;
  animation-delay: inherit;
}

/* 종이 3장이 0.5초 간격으로 차례로 올라옴 */
.pf-paper:nth-child(3) { left: 11%; animation-delay: 0s; }
.pf-paper:nth-child(4) { left: 15%; animation-delay: calc(var(--d) / 3); }
.pf-paper:nth-child(5) { left: 19%; animation-delay: calc(var(--d) * 2 / 3); }

.pf-front {
  left: 0;
  right: 0;
  top: 36%;
  bottom: 0;
  border-radius: 10% / 16%;
  background: linear-gradient(#cfc9f3, #b9b1e3);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transform-origin: 50% 100%;
  animation: pf-bump calc(var(--d) / 3) ease-out infinite;
}

@keyframes pf-rise {
  0% { transform: translateY(0) rotate(0); }
  11% { transform: translateY(-44%) rotate(-7deg); }
  22% { transform: translateY(-48%) rotate(5deg); }
  33%, 100% { transform: translateY(0) rotate(0); }
}

@keyframes pf-scan {
  0%, 9% { opacity: 0; top: 20%; }
  12% { opacity: 0.9; top: 20%; }
  24% { opacity: 0.9; top: 72%; }
  28%, 100% { opacity: 0; top: 72%; }
}

@keyframes pf-bump {
  0%, 60% { transform: scaleY(1); }
  72% { transform: scaleY(0.95); }
  100% { transform: scaleY(1); }
}

/* 정지 상태 - 종이 3장이 부채꼴로 살짝 나와 있음 */
.pf.still .pf-paper,
.pf.still .pf-paper::after,
.pf.still .pf-front {
  animation: none;
}

.pf.still .pf-paper::after { display: none; }
.pf.still .pf-paper:nth-child(3) { transform: translateY(-34%) rotate(-8deg); }
.pf.still .pf-paper:nth-child(4) { transform: translateY(-40%) rotate(1deg); }
.pf.still .pf-paper:nth-child(5) { transform: translateY(-34%) rotate(8deg); }

@media (prefers-reduced-motion: reduce) {
  .pf-paper, .pf-paper::after, .pf-front { animation: none; }
}
</style>
