<script setup>
// 대시보드 - 신규 가입자 추이 라인차트 (v3.html PAGE 7 DASHBOARD 재현, 스크롤 진입 시 왼쪽부터 그려짐)
// 가입자가 없는 날은 아예 그래프에서 빼고, 가입이 있었던 날짜만 점으로 찍는다.
// 포인트별로 겹치는 원을 올려두면 호버가 깜빡거려서, 전체 영역에 마우스무브로 가장 가까운 점을 찾는 방식으로 처리.
import { computed, ref } from 'vue'

const props = defineProps({
  signupTrend: { type: Array, required: true }, // [{ date: 'YYYY-MM-DD', newCount, cumulative }]
  revealed: { type: Boolean, default: false },
})

const W = 480
const H = 160
const PAD = 8
// 툴팁은 어떤 점을 가리키든 항상 그래프 오른쪽 위 여백(빈 공간)에 고정해서 뜨고, 실제 점까지는
// 선으로만 이어준다 - 점 바로 옆에 뜨면 그래프 중간을 가릴 때가 많아서 아예 위치를 고정함
const CORNER_X = W - 12
const CORNER_Y = 14

const svgEl = ref(null)
const hoveredIndex = ref(null)

function shortDate(dateStr) {
  const m = Number(dateStr.slice(5, 7))
  const d = Number(dateStr.slice(8, 10))
  return `${m}/${d}`
}

const signupDays = computed(() => props.signupTrend.filter((p) => p.newCount > 0))

// 특이값(가입 이벤트로 하루에 몰리는 경우 등) 때문에 그래프 높낮이가 과하게 튀지 않도록 제곱근 스케일 사용
const points = computed(() => {
  const days = signupDays.value
  const scaled = days.map((p) => Math.sqrt(p.newCount))
  const max = Math.max(1, ...scaled)
  return days.map((p, i) => {
    const x = PAD + (i / Math.max(1, days.length - 1)) * (W - PAD * 2)
    const y = H - PAD - (scaled[i] / max) * (H - PAD * 2)
    return { x, y, ...p }
  })
})

const linePath = computed(() =>
  points.value.length ? 'M' + points.value.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L') : '',
)

const areaPath = computed(() => {
  if (!points.value.length) return ''
  return `${linePath.value} L${(W - PAD).toFixed(1)},${(H - PAD).toFixed(1)} L${PAD.toFixed(1)},${(H - PAD).toFixed(1)} Z`
})

const signupTotal = computed(() => props.signupTrend.reduce((sum, p) => sum + p.newCount, 0))

function onMove(event) {
  if (!points.value.length || !svgEl.value) return
  const rect = svgEl.value.getBoundingClientRect()
  const svgX = ((event.clientX - rect.left) / rect.width) * W
  let nearest = 0
  let nearestDist = Infinity
  points.value.forEach((p, i) => {
    const dist = Math.abs(p.x - svgX)
    if (dist < nearestDist) {
      nearestDist = dist
      nearest = i
    }
  })
  hoveredIndex.value = nearest
}

function onLeave() {
  hoveredIndex.value = null
}

const hoveredPoint = computed(() =>
  hoveredIndex.value != null ? points.value[hoveredIndex.value] : null,
)

const tooltipStyle = computed(() => {
  if (!hoveredPoint.value || !svgEl.value) return {}
  const rect = svgEl.value.getBoundingClientRect()
  const x = rect.left + (CORNER_X / W) * rect.width
  const y = rect.top + (CORNER_Y / H) * rect.height
  return { left: x + 'px', top: y + 'px', transform: 'translate(-100%, 0)' }
})
</script>

<template>
  <div class="chart-card">
    <div class="chart-title">🙋 신규 가입자 추이</div>
    <div class="chart-sub">기간 내 신규 {{ signupTotal }}명 · 마우스를 올리면 수치가 보여요</div>
    <div class="chart-svg-wrap">
      <svg ref="svgEl" :viewBox="`0 0 ${W} ${H}`" class="line-svg" @mousemove="onMove" @mouseleave="onLeave">
        <path :d="areaPath" fill="#16A34A" opacity="0.1"></path>
        <path
          :d="linePath"
          fill="none"
          stroke="#16A34A"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
          :style="revealed ? { strokeDasharray: 1000, animation: 'draw-line 2.6s ease-out both' } : { strokeDasharray: 1000, strokeDashoffset: 1000 }"
        ></path>
        <circle v-for="p in points" :key="p.date" :cx="p.x" :cy="p.y" r="4" fill="#16A34A"></circle>
        <template v-if="hoveredPoint">
          <line
            :x1="hoveredPoint.x"
            :y1="hoveredPoint.y"
            :x2="CORNER_X"
            :y2="CORNER_Y"
            stroke="#1A1A2E"
            stroke-width="1"
            stroke-dasharray="3 3"
            opacity="0.35"
          ></line>
          <circle :cx="hoveredPoint.x" :cy="hoveredPoint.y" r="5.5" fill="#16A34A" stroke="#fff" stroke-width="1.5"></circle>
        </template>
      </svg>
      <div class="labels-row">
        <div v-for="p in points" :key="p.date" class="point-label" :style="{ left: ((p.x / W) * 100).toFixed(2) + '%' }">
          {{ shortDate(p.date) }}
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="hoveredPoint" class="point-tooltip" :style="tooltipStyle">
        {{ shortDate(hoveredPoint.date) }} · 신규 {{ hoveredPoint.newCount }}명 · 누적 {{ hoveredPoint.cumulative }}명
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
@keyframes draw-line {
  from {
    stroke-dashoffset: 1000;
  }
  to {
    stroke-dashoffset: 0;
  }
}

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
  margin-bottom: 14px;
}

.chart-svg-wrap {
  position: relative;
  margin-top: 10px;
}

.line-svg {
  width: 100%;
  height: 160px;
  overflow: visible;
  cursor: crosshair;
}

.labels-row {
  position: relative;
  height: 16px;
}

.point-label {
  position: absolute;
  top: 100%;
  transform: translate(-50%, 2px) rotate(-50deg);
  transform-origin: top left;
  font-size: 9px;
  color: #a0a4ac;
  white-space: nowrap;
}

.point-tooltip {
  position: fixed;
  background: #1a1a2e;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 6px 10px;
  border-radius: 7px;
  white-space: nowrap;
  z-index: 1000;
  pointer-events: none;
}
</style>
