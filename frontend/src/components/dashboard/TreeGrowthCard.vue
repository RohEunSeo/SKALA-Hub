<script setup>
// 대시보드 - 누적 게시글 수 기준 나무 성장 카드 (v3.html PAGE 7 DASHBOARD 나무 섹션 재현)
import { computed } from 'vue'

const props = defineProps({
  treeStage: { type: Object, required: true }, // { emoji, label, totalPostCount, nextThreshold, progressPct, barMax }
})

// 6단계 - 숫자는 "그 단계를 완성하는 데 필요한 누적 게시글 수"(다음 단계로 넘어가는 기준값) 기준.
// 숫자 자체는 진행률 바 위쪽 줄에, 이모지·이름은 바 아래쪽 줄에 나눠서 표시해 한 줄에 정보가 몰리지 않게 한다
const STAGE_MARKS = [
  { count: 0, emoji: '🌰', label: '씨앗' },
  { count: 100, emoji: '🌱', label: '새싹' },
  { count: 200, emoji: '🌿', label: '줄기' },
  { count: 300, emoji: '🪴', label: '어린 나무' },
  { count: 400, emoji: '🌲', label: '자라는 나무' },
  { count: 500, emoji: '🌳', label: '무성한 나무' },
]

const barMax = computed(() => props.treeStage.barMax || 500)

// 백엔드는 각 단계를 "시작 지점" 기준으로 이름 붙이지만(0=새싹,100=줄기,200=어린 나무...), 이 카드는
// 씨앗을 추가하고 "다음 단계까지 필요한 수"로 라벨을 붙여서 전체가 한 칸씩 밀렸다(0=씨앗,100=새싹,200=줄기...).
// 그래서 상단 큰 이모지·이름도 백엔드 값을 그대로 쓰지 않고 이 카드 자체의 기준으로 다시 계산해야
// 아래 눈금(예: 200=줄기)과 어긋나 보이지 않는다
const currentStage = computed(() => {
  const count = props.treeStage.totalPostCount ?? 0
  if (count < 100) return STAGE_MARKS[0] // 씨앗
  if (count < 200) return STAGE_MARKS[1] // 새싹
  if (count < 300) return STAGE_MARKS[2] // 줄기
  if (count < 400) return STAGE_MARKS[3] // 어린 나무
  return STAGE_MARKS[4] // 자라는 나무
})

// 바 위 눈금선(50 단위) - 시작/끝 지점(0, barMax)과 겹치지 않는 안쪽 지점에만 그린다
const barGridlines = computed(() => {
  const max = barMax.value
  const lines = []
  for (let v = 50; v < max; v += 50) lines.push(v)
  return lines
})

function gridlineLeftPct(v) {
  return (v / barMax.value) * 100
}

// 모든 칸을 동일하게 그 눈금 위치에 중앙정렬한다 - 양 끝만 다르게 붙이면 이모지가 눈금에서
// 벗어나 보이므로, 대신 카드 폭을 넉넉히 줘서(.tree-card) 살짝 삐져나가도 안 잘리게 한다
</script>

<template>
  <div class="tree-card">
    <div class="tree-title">🌱 허브 단계</div>
    <div class="tree-emoji">{{ currentStage.emoji }}</div>
    <div class="tree-label">{{ currentStage.label }}</div>
    <div class="tick-numbers">
      <span
        v-for="stage in STAGE_MARKS"
        :key="'num:' + stage.count"
        class="tick-number"
        :style="{ left: gridlineLeftPct(stage.count) + '%' }"
      >
        {{ stage.count }}
      </span>
    </div>
    <div class="tree-bar-track">
      <div class="tree-bar-fill" :style="{ width: treeStage.progressPct + '%' }"></div>
      <div
        v-for="v in barGridlines"
        :key="v"
        class="bar-gridline"
        :style="{ left: gridlineLeftPct(v) + '%' }"
      ></div>
    </div>
    <div class="tick-row">
      <div
        v-for="stage in STAGE_MARKS"
        :key="stage.count"
        class="tick"
        :style="{ left: gridlineLeftPct(stage.count) + '%' }"
      >
        <span class="tick-emoji">{{ stage.emoji }}</span>
        <span class="tick-label">{{ stage.label }}</span>
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

/* 폭을 넉넉히 줘야 6단계 라벨이 서로 안 겹친다. 오른쪽 패딩은 마지막(무성한 나무) 라벨이
   중앙정렬된 채 절반쯤 삐져나와도 구분선(border-right)과 안 겹칠 만큼 넉넉하게 둔다 */
.tree-card {
  width: clamp(230px, 30vw, 320px);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  text-align: center;
  padding-right: 28px;
  border-right: 1px solid #f0f0f2;
}

.tree-title {
  align-self: flex-start;
  margin-top: 0;
  font-size: 15px;
  font-weight: 800;
  line-height: 18px;
  color: #1a1a2e;
  margin-bottom: 14px;
}

.tree-emoji {
  font-size: 90px;
  line-height: 1;
  margin-top: 6px;
  animation:
    tree-grow 0.7s ease both,
    tree-sway 3s ease-in-out infinite;
  transform-origin: bottom center;
}

.tree-label {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}

/* 진행률 바 위쪽에 뜨는 0/100/200/300/400/500 숫자 줄 - 바 아래는 이모지·이름만 있어 복잡하지 않게 */
.tick-numbers {
  position: relative;
  width: 100%;
  height: 12px;
  margin-top: 18px;
}

.tick-number {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 8.5px;
  color: #a0a4ac;
  font-weight: 600;
  white-space: nowrap;
}

.tree-bar-track {
  position: relative;
  margin-top: 6px;
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

.bar-gridline {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: rgba(255, 255, 255, 0.55);
  pointer-events: none;
}

/* 절대 위치 - 바 위 눈금선과 정확히 같은 left%(count/barMax) 기준이라 서로 어긋나지 않는다 */
.tick-row {
  position: relative;
  width: 100%;
  height: 38px;
  margin-top: 6px;
}

.tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.tick-emoji {
  font-size: 15px;
}

.tick-label {
  font-size: 8px;
  color: #636e72;
  font-weight: 700;
  white-space: nowrap;
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
