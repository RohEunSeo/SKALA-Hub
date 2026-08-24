<script setup>
// 대시보드 - 신규 가입자 추이 라인차트 (v3.html PAGE 7 DASHBOARD 재현, 스크롤 진입 시 왼쪽부터 그려짐)
// 가입자가 없는 날은 아예 그래프에서 빼고, 가입이 있었던 날짜만 점으로 찍는다.
// 포인트별로 겹치는 원을 올려두면 호버가 깜빡거려서, 전체 영역에 마우스무브로 가장 가까운 점을 찾는 방식으로 처리.
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  signupTrend: { type: Array, required: true }, // [{ date: 'YYYY-MM-DD', newCount, cumulative }]
  revealed: { type: Boolean, default: false },
})

const W = 480
const H = 160
const PAD = 8
// 좌우는 위아래보다 여백을 더 둔다 - 맨 끝 점의 회전된 날짜 라벨이 옆으로 삐져나가도 잘리지 않게 하기 위함
const PAD_X = 20
// 툴팁은 항상 호버한 점 바로 위쪽 대각선에, 최대한 가깝게 띄운다
const POINT_GAP = 14
// 라벨 하나가 옆 라벨과 안 겹치는 데 필요한 최소 실제 가로 간격(px, 회전된 상태 기준)
const MIN_LABEL_GAP_PX = 15

const svgEl = ref(null)
const wrapEl = ref(null)
const hoveredIndex = ref(null)

// 날짜가 많을 때 카드 폭이 좁아지면(모바일 캐러셀/중간폭 2열 등) 점 사이 실제 간격이 라벨 폭보다 좁아져서
// 라벨끼리 겹쳐 앞쪽 라벨이 안 보이는 것처럼 되는 문제 - ResizeObserver로 실제 렌더링 폭을 추적해서
// 겹치지 않을 만큼만 라벨을 솎아내되, 첫 번째/마지막 라벨은 항상 표시한다
const wrapWidth = ref(W)
let resizeObserver = null

onMounted(() => {
  if (wrapEl.value) {
    resizeObserver = new ResizeObserver((entries) => {
      wrapWidth.value = entries[0].contentRect.width || W
    })
    resizeObserver.observe(wrapEl.value)
  }
})

onUnmounted(() => resizeObserver?.disconnect())

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
    const x = PAD_X + (i / Math.max(1, days.length - 1)) * (W - PAD_X * 2)
    const y = H - PAD - (scaled[i] / max) * (H - PAD * 2)
    return { x, y, ...p }
  })
})

const linePath = computed(() =>
  points.value.length ? 'M' + points.value.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L') : '',
)

// 점(데이터)은 다 그리되, 라벨은 실제 폭에서 겹치지 않을 만큼만 몇 개 걸러서 보여준다.
// 항상 첫 점(예: 7/28)과 마지막 점은 포함시켜서 시작/끝 날짜가 안 잘려 보이게 한다
const visibleLabelIndexes = computed(() => {
  const n = points.value.length
  if (n === 0) return new Set()
  if (n === 1) return new Set([0])
  const pxPerPoint = wrapWidth.value / (n - 1)
  const step = Math.max(1, Math.ceil(MIN_LABEL_GAP_PX / pxPerPoint))
  const result = new Set()
  for (let i = 0; i < n; i += step) result.add(i)
  result.add(n - 1)
  return result
})

const areaPath = computed(() => {
  if (!points.value.length) return ''
  return `${linePath.value} L${(W - PAD_X).toFixed(1)},${(H - PAD).toFixed(1)} L${PAD_X.toFixed(1)},${(H - PAD).toFixed(1)} Z`
})

// 펜으로 그리듯 왼쪽부터 그려지는 효과 - dasharray/offset이 실제 경로 길이와 맞아야 처음엔 완전히 안 보이다가
// 끝까지 고르게 그려진다 (하드코딩된 값을 쓰면 경로가 짧을 때 절반 이상 안 보이다 막판에 갑자기 나타나 보임)
const linePathLength = computed(() => {
  const pts = points.value
  let len = 0
  for (let i = 1; i < pts.length; i++) {
    len += Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
  }
  return len || 1
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

// 점 바로 위 대각선에 띄우되, 오른쪽 끝/위쪽 끝 근처 점은 반대 방향으로 뒤집어서 카드 밖으로 나가지 않게 한다.
// x가 점의 실제 위치를 그대로 따라가므로 7월 데이터는 왼쪽에, 8월 데이터는 오른쪽에 자연스럽게 뜬다
const tooltipAnchor = computed(() => {
  if (!hoveredPoint.value) return null
  const p = hoveredPoint.value
  const placeLeft = p.x > W - 70
  const placeBelow = p.y < PAD + 40
  return {
    x: placeLeft ? p.x - POINT_GAP : p.x + POINT_GAP,
    y: placeBelow ? p.y + POINT_GAP : p.y - POINT_GAP,
    placeLeft,
    placeBelow,
  }
})

const tooltipStyle = computed(() => {
  if (!tooltipAnchor.value || !svgEl.value) return {}
  const rect = svgEl.value.getBoundingClientRect()
  const { x, y, placeLeft, placeBelow } = tooltipAnchor.value
  const left = rect.left + (x / W) * rect.width
  const top = rect.top + (y / H) * rect.height
  return {
    left: left + 'px',
    top: top + 'px',
    transform: `translate(${placeLeft ? '-100%' : '0%'}, ${placeBelow ? '0%' : '-100%'})`,
  }
})
</script>

<template>
  <div class="chart-card">
    <div class="chart-title">🙋 신규 가입자 추이</div>
    <div class="chart-sub">기간 내 신규 {{ signupTotal }}명 · 마우스를 올리면 수치가 보여요</div>
    <div ref="wrapEl" class="chart-svg-wrap">
      <svg ref="svgEl" :viewBox="`0 0 ${W} ${H}`" class="line-svg" @mousemove="onMove" @mouseleave="onLeave">
        <path :d="areaPath" fill="#16A34A" opacity="0.1"></path>
        <path
          :d="linePath"
          class="draw-line"
          fill="none"
          stroke="#16A34A"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
          :style="{ strokeDasharray: linePathLength, strokeDashoffset: revealed ? 0 : linePathLength }"
        ></path>
        <circle v-for="p in points" :key="p.date" :cx="p.x" :cy="p.y" r="4" fill="#16A34A"></circle>
        <template v-if="hoveredPoint && tooltipAnchor">
          <line
            :x1="hoveredPoint.x"
            :y1="hoveredPoint.y"
            :x2="tooltipAnchor.x"
            :y2="tooltipAnchor.y"
            stroke="#1A1A2E"
            stroke-width="1"
            stroke-dasharray="3 3"
            opacity="0.35"
          ></line>
          <circle :cx="hoveredPoint.x" :cy="hoveredPoint.y" r="5.5" fill="#16A34A" stroke="#fff" stroke-width="1.5"></circle>
        </template>
      </svg>
      <div class="labels-row">
        <template v-for="(p, i) in points" :key="p.date">
          <div
            v-if="visibleLabelIndexes.has(i)"
            class="point-label"
            :class="{ 'point-label-first': i === 0, 'point-label-last': i === points.length - 1 }"
            :style="{ left: ((p.x / W) * 100).toFixed(2) + '%' }"
          >
            {{ shortDate(p.date) }}
          </div>
        </template>
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
/* stroke-dashoffset을 실제 경로 길이 → 0으로 transition시켜서 펜으로 그리듯 왼쪽부터 그려지게 한다.
   LoginBarChart의 막대 height transition과 같은 방식이라 revealed가 false→true→false로 바뀔 때마다
   (스크롤로 나갔다 다시 들어올 때) 매번 다시 재생된다 */
.draw-line {
  transition: stroke-dashoffset 2.6s ease-out;
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

/* 왼쪽에 여유 버퍼를 둬서, 맨 첫 점의 회전된 날짜 라벨이 모바일 캐러셀 슬라이드 경계 밖으로
   삐져나가도 잘리지 않게 한다 (라벨은 svg가 아니라 별도 HTML이라 폭이 좁아지면 상대적으로 더 크게 튀어나옴) */
.chart-svg-wrap {
  position: relative;
  margin-top: 10px;
  padding-left: 10px;
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

/* 맨 첫 라벨만 가운데 정렬(-50%)을 빼서, 회전 기준점이 그 점의 실제 위치에 고정된다.
   -50deg 회전은 원점보다 왼쪽으로는 절대 나가지 않으므로 어떤 폭에서도 수학적으로 잘릴 수 없다 */
.point-label-first {
  transform: translate(0, 2px) rotate(-50deg);
}

/* 맨 마지막 라벨은 반대로 오른쪽 끝을 기준점으로 고정해서, 회전해도 그 점보다 오른쪽으로는
   절대 나가지 않는다 (첫 라벨과 대칭되는 방식) */
.point-label-last {
  transform: translate(-100%, 2px) rotate(-50deg);
  transform-origin: top right;
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
