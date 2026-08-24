<script setup>
// 대시보드 - 누적 게시글 수 기준 나무 성장 카드 (v3.html PAGE 7 DASHBOARD 나무 섹션 재현)
defineProps({
  treeStage: { type: Object, required: true }, // { emoji, label, totalPostCount, nextThreshold, progressPct, barMax }
})

// 바 아래 눈금 - 실제 나무 성장 단계 전환 지점(100/200/300/400)과 정확히 같은 이모지를 써야
// "지금 몇 단계인지"와 눈금이 서로 안 맞아 보이지 않는다. 500은 별도 단계가 없어 마지막(무성한 나무) 재사용
const TICKS = [
  { count: 100, emoji: '🌿' }, // 줄기
  { count: 200, emoji: '🪴' }, // 어린 나무
  { count: 300, emoji: '🌲' }, // 자라는 나무
  { count: 400, emoji: '🌳' }, // 무성한 나무
  { count: 500, emoji: '🌳' },
]
</script>

<template>
  <div class="tree-card">
    <div class="tree-emoji">{{ treeStage.emoji }}</div>
    <div class="tree-label">{{ treeStage.label }}</div>
    <div class="tree-bar-track">
      <div class="tree-bar-fill" :style="{ width: treeStage.progressPct + '%' }"></div>
    </div>
    <div class="tick-row">
      <div v-for="tick in TICKS" :key="tick.count" class="tick">
        <span class="tick-emoji">{{ tick.emoji }}</span>
        <span class="tick-count">{{ tick.count }}</span>
      </div>
    </div>
    <div class="tree-progress-text">
      누적 게시글 <span class="tree-progress-value">{{ treeStage.totalPostCount }}개</span>
    </div>
  </div>
</template>

<style scoped>
@keyframes tree-grow {
  from {
    transform: scaleY(0.2) translateY(20px);
    opacity: 0;
  }
  to {
    transform: scaleY(1) translateY(0);
    opacity: 1;
  }
}

@keyframes tree-sway {
  0%,
  100% {
    transform: rotate(-4deg);
  }
  50% {
    transform: rotate(4deg);
  }
}

.tree-card {
  width: 284px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding-right: 16px;
  border-right: 1px solid #f0f0f2;
}

.tree-emoji {
  font-size: 100px;
  line-height: 1;
  margin-top: 6px;
  animation:
    tree-grow 0.7s ease both,
    tree-sway 3s ease-in-out infinite;
  transform-origin: bottom center;
}

.tree-label {
  margin-top: 2px;
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}

.tree-bar-track {
  margin-top: 26px;
  width: 100%;
  height: 8px;
  border-radius: 6px;
  background: #ede9fe;
  overflow: hidden;
}

.tree-bar-fill {
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, #7c3aed, #4a3f8f);
  transition: width 1s ease;
}

.tick-row {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin-top: 6px;
}

.tick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.tick-emoji {
  font-size: 15px;
}

.tick-count {
  font-size: 8.5px;
  color: #a0a4ac;
  font-weight: 600;
}

.tree-progress-text {
  margin-top: 10px;
  font-size: 12.5px;
  color: #636e72;
}

.tree-progress-value {
  color: #4a3f8f;
  font-weight: 800;
}

@media (max-width: 768px) {
  .tree-card {
    width: 100%;
    padding-right: 0;
    border-right: none;
    border-bottom: 1px solid #f0f0f2;
    padding-bottom: 20px;
  }
}
</style>
