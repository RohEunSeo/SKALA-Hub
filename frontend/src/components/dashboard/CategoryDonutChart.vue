<script setup>
// 대시보드 - 카테고리별 분포 도넛차트 (v3.html PAGE 7 DASHBOARD 재현, 스크롤 진입 시 시계방향 0~360도로 채워짐)
import { computed, onUnmounted, ref, watch } from 'vue'
import { CATEGORIES } from '../../constants/categories'
import { useHoverTooltip } from '../../composables/useHoverTooltip'

const props = defineProps({
  categoryDist: { type: Array, required: true }, // [{ category, count, pct }]
  revealed: { type: Boolean, default: false },
})

const { hoveredKey, rect, onEnter, onLeave } = useHoverTooltip()

const revealProgress = ref(0)
let raf = null

function startReveal() {
  cancelAnimationFrame(raf)
  const start = performance.now()
  const duration = 1600
  const tick = (now) => {
    const p = Math.min(1, (now - start) / duration)
    revealProgress.value = p
    if (p < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
}

function stopReveal() {
  cancelAnimationFrame(raf)
  revealProgress.value = 0
}

// immediate 없이는 "이미 revealed=true인 채로" 컴포넌트가 새로 마운트될 때(예: 반응형 브레이크포인트를
// 넘나들며 모바일 캐러셀 ↔ 데스크탑 그리드로 다시 마운트될 때) 값이 "바뀌지" 않아서 애니메이션이 아예
// 시작되지 않고 도넛이 반지름 0인 채로 안 보이는 버그가 있었음
watch(
  () => props.revealed,
  (val) => (val ? startReveal() : stopReveal()),
  { immediate: true },
)
onUnmounted(() => cancelAnimationFrame(raf))

function colorFor(category) {
  return CATEGORIES.find((c) => c.value === category)?.color ?? '#8890A3'
}

function shortLabel(category) {
  return CATEGORIES.find((c) => c.value === category)?.shortLabel ?? category
}

const total = computed(() => props.categoryDist.reduce((sum, c) => sum + c.count, 0))

// 범례 목록은 비율 높은 게 위로 오도록 정렬 (파이 조각은 반대로 원래 카테고리 순서 그대로 둬야
// 비율 작은 조각끼리 몰려서 안 보이는 문제가 없음)
const sortedDist = computed(() => [...props.categoryDist].sort((a, b) => b.pct - a.pct))

const segs = computed(() => {
  const R = 50;
  const CX = 60;
  const CY = 60
  let cum = -Math.PI / 2
  return props.categoryDist
    .filter((c) => c.count > 0)
    .map((c) => {
      const angle = total.value ? (c.count / total.value) * 2 * Math.PI : 0
      const x1 = CX + R * Math.cos(cum);
      const y1 = CY + R * Math.sin(cum)
      const end = cum + angle
      const x2 = CX + R * Math.cos(end);
      const y2 = CY + R * Math.sin(end)
      const largeArc = angle > Math.PI ? 1 : 0
      const path = `M${CX},${CY} L${x1.toFixed(1)},${y1.toFixed(1)} A${R},${R} 0 ${largeArc} 1 ${x2.toFixed(1)},${y2.toFixed(1)} Z`
      cum = end
      return { ...c, path, color: colorFor(c.category), key: 'cat:' + c.category }
    })
})

const clipPath = computed(() => {
  const theta = revealProgress.value * 359.99
  const rad = (theta * Math.PI) / 180
  const x = 60 + 60 * Math.sin(rad);
  const y = 60 - 60 * Math.cos(rad)
  const largeArc = theta > 180 ? 1 : 0
  return revealProgress.value <= 0 ? 'M60,60 Z' : `M60,60 L60,0 A60,60 0 ${largeArc} 1 ${x.toFixed(2)},${y.toFixed(2)} Z`
})

const clipAttr = computed(() => (revealProgress.value < 1 ? 'url(#donut-reveal-clip)' : 'none'))

const hoveredSeg = computed(() => segs.value.find((s) => s.key === hoveredKey.value))

// 조각 위 근처에 %만 뜨도록 - 가운데 원/구멍 등 다른 효과는 추가하지 않는다
const tooltipStyle = computed(() => {
  if (!rect.value) return {}
  return {
    left: rect.value.left + rect.value.width / 2 + 'px',
    top: rect.value.top + rect.value.height / 2 + 'px',
    transform: 'translate(-50%, -50%)',
  }
})
</script>

<template>
  <div class="chart-card">
    <div class="chart-title">🗂️ 카테고리별 분포</div>
    <div class="chart-sub">조각에 마우스를 올리면 게시글 개수가 표시됩니다</div>
    <div class="donut-body">
      <div class="donut-wrap">
        <svg viewBox="0 0 120 120" class="donut-svg">
          <defs>
            <clipPath id="donut-reveal-clip">
              <path :d="clipPath"></path>
            </clipPath>
          </defs>
          <g :clip-path="clipAttr">
            <path
              v-for="s in segs"
              :key="s.key"
              :d="s.path"
              :fill="s.color"
              stroke="#FFFFFF"
              stroke-width="1.5"
              @mouseenter="onEnter($event, s.key)"
              @mouseleave="onLeave"
            ></path>
          </g>
        </svg>
      </div>
      <div class="legend-list">
        <div v-for="c in sortedDist" :key="c.category" class="legend-row">
          <div class="legend-left">
            <span class="legend-dot" :style="{ background: colorFor(c.category) }"></span>
            <span class="legend-label">{{ shortLabel(c.category) }}</span>
          </div>
          <span class="legend-pct">{{ Math.round(c.pct) }}%</span>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="hoveredSeg" class="seg-tooltip" :style="tooltipStyle">{{ hoveredSeg.count }}개</div>
    </Teleport>
  </div>
</template>

<style scoped>
.chart-card {
  height: 100%;
  box-sizing: border-box;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 1px 5px rgba(26, 26, 46, 0.05);
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.chart-title {
  font-size: 15px;
  font-weight: 800;
  color: #1a1a2e;
  margin-bottom: 4px;
}

.chart-sub {
  font-size: 12px;
  color: #636e72;
  margin-bottom: 10px;
}

.donut-body {
  display: flex;
  flex-direction: column;
}

.donut-wrap {
  display: flex;
  justify-content: center;
  margin-top: 10px;
  margin-bottom: 16px;
}

.donut-svg {
  width: 128px;
  height: 128px;
  overflow: visible;
}

.donut-svg path {
  cursor: pointer;
}

.legend-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.legend-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
}

.legend-left {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.legend-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-label {
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-pct {
  color: #636e72;
  font-weight: 600;
  flex-shrink: 0;
}

.seg-tooltip {
  position: fixed;
  background: #1a1a2e;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 7px;
  white-space: nowrap;
  z-index: 1000;
  pointer-events: none;
}

/* 화면이 좁아지는 중간 구간(768~1180px)에서는 StatsRow가 이 카드를 2열 그리드 아래 한 줄 전체로 배치하므로,
   그 넓어진 폭을 살려 원(도넛)을 왼쪽에 좀 더 크게, 범례를 오른쪽에 두는 가로 배치로 전환한다.
   grid-column은 부모가 grid(.stats-grid)일 때만 의미가 있고 flex(carousel)일 때는 무시된다 */
@media (max-width: 1180px) {
  .chart-card {
    grid-column: 1 / -1;
  }

  .donut-body {
    flex-direction: row;
    align-items: center;
    gap: 24px;
  }

  .donut-wrap {
    margin: 0;
    flex-shrink: 0;
  }

  .donut-svg {
    width: 156px;
    height: 156px;
  }

  .legend-list {
    flex: 1;
    justify-content: center;
  }
}
</style>
