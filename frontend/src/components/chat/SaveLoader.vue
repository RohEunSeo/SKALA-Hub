<script setup>
// AI 챗봇 "폴더에 저장하는 중" 로더 - 문서가 위에서 폴더로 쏙 들어가는 모션 (검색 로더와 반대 방향).
// CSS만 사용, width(px)/duration(초)로 조절
// color: 선택한 폴더의 색 (폴더 없이 저장이면 기본 보라)
defineProps({ width: { type: Number, default: 52 }, duration: { type: Number, default: 2.7 }, color: { type: String, default: '#b7a6e6' } })
</script>

<template>
  <span class="sv" :style="{ '--w': `${width}px`, '--d': `${duration}s`, '--fc': color }" role="img" aria-label="저장 중">
    <span class="sv-tab"></span>
    <span class="sv-back"></span>
    <span class="sv-paper"></span>
    <span class="sv-paper"></span>
    <span class="sv-paper"></span>
    <span class="sv-front"></span>
  </span>
</template>

<style scoped>
.sv {
  position: relative;
  display: inline-block;
  width: var(--w);
  aspect-ratio: 48 / 40;
  flex: none;
}

.sv > span {
  position: absolute;
  display: block;
}

.sv-tab {
  left: 0;
  top: 0;
  width: 42%;
  height: 22%;
  border-radius: 12% 12% 0 0 / 30% 30% 0 0;
  background: var(--fc);
}

.sv-back {
  left: 0;
  right: 0;
  top: 10%;
  bottom: 0;
  border-radius: 10% / 12%;
  background: var(--fc);
}

.sv-paper {
  top: 16%;
  left: 14%;
  height: 56%;
  width: 70%;
  border-radius: 6% / 8%;
  background: #ffffff repeating-linear-gradient(to bottom, transparent 0 18%, #dcd7f2 18% 26%) 20% 30% / 60% 60% no-repeat;
  box-shadow: 0 1px 2px rgba(72, 64, 138, 0.18);
  opacity: 0;
  animation: sv-drop var(--d) cubic-bezier(0.5, 0, 0.4, 1) infinite;
}

/* 문서 3장이 차례로 떨어져 들어감 */
.sv-paper:nth-child(4) { animation-delay: calc(var(--d) / 3); left: 18%; }
.sv-paper:nth-child(5) { animation-delay: calc(var(--d) * 2 / 3); left: 22%; }

.sv-front {
  left: 0;
  right: 0;
  top: 36%;
  bottom: 0;
  border-radius: 10% / 16%;
  background: linear-gradient(rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0.08)), var(--fc);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transform-origin: 50% 100%;
  animation: sv-bump calc(var(--d) / 3) ease-out infinite;
}

/* 위에서 내려와 폴더 앞판 뒤로 사라짐 (한 바퀴 = 문서 1장 분량의 1/3 구간) */
@keyframes sv-drop {
  0% { opacity: 0; transform: translateY(-150%) rotate(-10deg); }
  12% { opacity: 1; }
  30% { opacity: 1; transform: translateY(28%) rotate(0); }
  36%, 100% { opacity: 0; transform: translateY(28%) rotate(0); }
}

@keyframes sv-bump {
  0%, 60% { transform: scaleY(1); }
  75% { transform: scaleY(0.94); }
  100% { transform: scaleY(1); }
}

@media (prefers-reduced-motion: reduce) {
  .sv-paper, .sv-front { animation: none; }
  .sv-paper:nth-child(4) { opacity: 1; }
}
</style>
