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
// 아래쪽 여백을 최소화해서 x축(그래프 바닥선)과 날짜 라벨이 바짝 붙어 보이게 한다
const PAD = 4
// 라벨 좌우 안전 버퍼는 이제 svg 내부가 아니라 chart-svg-wrap의 HTML padding-left/right로 처리하므로
// (모든 라벨이 동일한 transform-origin을 쓸 수 있게), 여기 PAD_X는 최소한으로 줄여서 실제 점이
// 찍히는 가로 폭 자체를 넓힌다
const PAD_X = 10
// 툴팁은 항상 호버한 점 바로 위쪽 대각선에, 최대한 가깝게 띄운다
const POINT_GAP = 14
// 라벨 하나가 옆 라벨과 안 겹치는 데 필요한 최소 실제 가로 간격(px, 8px 폰트 기준 -50deg 회전 폭)
const MIN_LABEL_GAP_PX = 20

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
// 예전엔 일정 간격(step)으로 걸러내고 마지막 점을 강제로 추가하는 방식이었는데, n-1이 step의
// 배수가 아니면 마지막 라벨 직전 간격만 다른 간격의 절반 이하로 좁아져서(예: 나머지 라벨은 2칸씩인데
// 마지막 라벨만 1칸 뒤) 점들 사이 간격이 고르지 않아 보였다. 대신 몇 개를 보여줄 수 있는지(count) 먼저
// 계산한 뒤, 0~n-1 구간에 그 개수를 인덱스 기준으로 고르게 분산시켜서 첫/끝을 포함해 전 구간이
// 일정한 간격으로 보이게 한다
const visibleLabelIndexes = computed(() => {
  const n = points.value.length
  if (n === 0) return new Set()
  if (n <= 1) return new Set([0])
  const maxLabels = Math.max(2, Math.floor(wrapWidth.value / MIN_LABEL_GAP_PX) + 1)
  const count = Math.min(n, maxLabels)
  const result = new Set()
  for (let k = 0; k < count; k++) {
    result.add(Math.round((k * (n - 1)) / (count - 1)))
  }
  return result
})

const areaPath = computed(() => {
  if (!points.value.length) return ''
  return `${linePath.value} L${(W - PAD_X).toFixed(1)},${(H - PAD).toFixed(1)} L${PAD_X.toFixed(1)},${(H - PAD).toFixed(1)} Z`
})

// 펜으로 그리듯 왼쪽부터 그려지는 효과 - dasharray/offset이 실제 경로 길이와 맞아야 처음엔 완전히 안 보이다가
// 끝까지 고르게 그려진다 (하드코딩된 값을 쓰면 경로가 짧을 때 절반 이상 안 보이다 막판에 갑자기 나타나 보임).
// 점별 누적 길이도 같이 구해둬서, 마일스톤 배지가 라인이 그 지점까지 그려지는 시점에 맞춰 나타나게 한다
const cumulativeLengths = computed(() => {
  const pts = points.value
  const lens = [0]
  for (let i = 1; i < pts.length; i++) {
    lens.push(lens[i - 1] + Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y))
  }
  return lens
})

const linePathLength = computed(() => cumulativeLengths.value[cumulativeLengths.value.length - 1] || 1)

// 라인이 실제로 왼쪽부터 그려지는 데 걸리는 시간(.draw-line의 stroke-dashoffset transition)과 동일한 값 -
// 마일스톤 배지 등장 딜레이를 여기에 맞춰 계산해야 "라인이 그 지점을 지나가는 순간" 배지가 나타난다
const LINE_DRAW_DURATION_S = 2.6

const signupTotal = computed(() => props.signupTrend.reduce((sum, p) => sum + p.newCount, 0))

// 누적 가입자 수가 50명 단위 고지를 처음 넘긴 날짜(가장 가까운 실제 데이터 점) 근처 여백에 고정
// 스티커를 띄운다(호버 없이 항상 보임). 기본은 점 위쪽(또는 맨 위쪽 근처면 아래쪽)에 두되, 100명
// 고지는 하필 꼭대기(극댓값) 점이라 위쪽에 두면 선/영역에 걸쳐서 요청대로 옆(오른쪽)에 따로 둔다.
// 등장 타이밍은 그 점까지의 누적 길이 비율만큼 라인 그리기 애니메이션(LINE_DRAW_DURATION_S)에
// 딜레이를 줘서, 라인이 실제로 그 지점을 지나가는 순간 순서대로(100 → 150 → 200) 나타나게 한다
const MILESTONE_THRESHOLDS = [100, 150, 200, 250, 300]
// 위쪽 대신 옆(오른쪽)에 띄울 고지 - 그 점이 그래프 꼭대기라 위로 띄울 여백이 없는 경우
const MILESTONE_SIDE_PLACEMENT = new Set([100])
// 150명 고지는 누적치 기준 자동 탐지가 7/31 점을 잡아서 불 이모지가 그 점과 겹쳤다 - 8/3 점 위로
// 고정하고 거기서 살짝 더 오른쪽으로 띄운다(100/200은 그대로 자동 탐지 유지)
const MILESTONE_DATE_OVERRIDE = { 150: '08-03' }
const MILESTONE_X_NUDGE = { 150 : 15 }

const milestoneBadges = computed(() => {
  const pts = points.value
  if (!pts.length) return []
  const total = linePathLength.value
  const byIndex = new Map()
  for (const threshold of MILESTONE_THRESHOLDS) {
    let idx = pts.findIndex((p) => p.cumulative >= threshold)
    if (idx === -1) continue
    const dateOverride = MILESTONE_DATE_OVERRIDE[threshold]
    if (dateOverride) {
      const overrideIdx = pts.findIndex((p) => p.date.slice(5) === dateOverride)
      if (overrideIdx !== -1) idx = overrideIdx
    }
    if (!byIndex.has(idx) || threshold > byIndex.get(idx)) byIndex.set(idx, threshold)
  }
  return Array.from(byIndex.entries()).map(([idx, threshold]) => {
    const p = pts[idx]
    const delay = (cumulativeLengths.value[idx] / total) * LINE_DRAW_DURATION_S
    const side = MILESTONE_SIDE_PLACEMENT.has(threshold)
    return {
      key: threshold,
      x: p.x + (MILESTONE_X_NUDGE[threshold] || 0),
      y: p.y,
      side,
      // 옆 배치: 오른쪽 끝 근처 점만 카드 밖으로 안 나가게 반대로(왼쪽) 뒤집는다
      // 위 배치: 그래프 맨 위쪽 근처 점만 반대로(아래) 뒤집는다
      flip: side ? p.x > W - 70 : p.y < PAD + 26,
      delay,
    }
  })
})

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
      <div class="svg-stage">
        <svg
          ref="svgEl"
          :viewBox="`0 0 ${W} ${H}`"
          preserveAspectRatio="none"
          class="line-svg"
          @mousemove="onMove"
          @mouseleave="onLeave"
        >
          <path :d="areaPath" fill="#16A34A" opacity="0.1"></path>
          <template v-for="(p, i) in points" :key="'guide:' + p.date">
            <line
              v-if="visibleLabelIndexes.has(i)"
              :x1="p.x"
              :y1="p.y"
              :x2="p.x"
              :y2="H - PAD"
              stroke="#c4c8d0"
              stroke-width="1"
              stroke-dasharray="2 3"
              opacity="0.6"
            ></line>
          </template>
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

        <div
          v-for="badge in milestoneBadges"
          :key="'milestone:' + badge.key"
          class="milestone-badge"
          :class="[
            badge.side ? 'milestone-badge-side' : 'milestone-badge-above',
            { 'milestone-badge-flip': badge.flip },
          ]"
          :style="{
            left: ((badge.x / W) * 100).toFixed(2) + '%',
            top: ((badge.y / H) * 100).toFixed(2) + '%',
            transitionDelay: badge.delay.toFixed(2) + 's',
            opacity: revealed ? 1 : 0,
          }"
        >
          🔥 {{ badge.key }}명 달성
        </div>
      </div>
      <div class="labels-row">
        <template v-for="(p, i) in points" :key="p.date">
          <div
            v-if="visibleLabelIndexes.has(i)"
            class="point-label"
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

/* 카드 좌우 패딩(24px) 안쪽으로 그래프가 갇혀 있으면 정작 그릴 수 있는 폭이 좁아지므로, 음수
   margin으로 카드 패딩 영역까지 밀고 나가서 실제 그래프 폭을 넓힌다. 여기엔 일부러 padding을 다시
   주지 않는다 - padding을 주면 라벨의 left:%(이 래퍼의 padding box 기준)와 svg의 실제 폭(width:100%,
   content box 기준 = padding box보다 좁음)이 서로 다른 기준으로 계산돼서 점점 어긋나 보였다(특히
   오른쪽 끝으로 갈수록 크게 벌어짐). 대신 맨 끝 라벨이 살짝 넘치면 방금 음수 margin으로 비워둔
   카드 패딩 영역(원래도 빈 공간)으로 자연스럽게 흘러나가게 둔다 */
.chart-svg-wrap {
  position: relative;
  margin: 10px -14px 0;
}

/* svg와 마일스톤 배지만 감싸는 별도 스테이지 - 라벨 줄(labels-row)을 밖으로 뺐기 때문에 이 안의
   높이가 정확히 svg 높이(160px)와 같아져서, 배지의 top:%(svg viewBox의 y/H 기준)가 정확히 맞아떨어진다.
   labels-row까지 포함된 전체 래퍼 기준으로 %를 계산하면 실제보다 더 아래로 밀려 보인다 */
.svg-stage {
  position: relative;
}

.line-svg {
  width: 100%;
  height: 160px;
  overflow: visible;
  cursor: crosshair;
}

/* 연한 초록 반투명 스티커 - 색 자체가 옅고 투명해서 혹시 살짝 걸쳐도 거슬리지 않는다. opacity는
   revealed에 따라 JS에서 직접 바꿔주고(:style), transition-delay를 라인이 그 지점까지 그려지는
   시점에 맞춰 개별로 줘서 - 라인이 그려지는 걸 따라 100 → 150 → 200 순서로 하나씩 나타난다
   (처음부터 다 떠 있지 않음) */
.milestone-badge {
  position: absolute;
  background: rgba(22, 163, 74, 0.14);
  border: 1px solid rgba(22, 163, 74, 0.3);
  color: #1a1a2e;
  font-size: 9px;
  font-weight: 800;
  padding: 3px 7px;
  border-radius: 999px;
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.5s ease;
  pointer-events: none;
}

/* 기본: 점 위쪽에 띄운다. 맨 위쪽 근처 점이라 위로 띄울 자리가 없으면(-flip) 반대로 점 아래에 둔다 */
.milestone-badge-above {
  transform: translate(-50%, calc(-100% - 12px));
}

.milestone-badge-above.milestone-badge-flip {
  transform: translate(-50%, 12px);
}

/* 100명 고지처럼 꼭대기(극댓값) 점이라 위쪽 여백이 없는 경우: 옆(오른쪽)에 띄운다. 오른쪽 끝 근처
   점이면(-flip) 카드 밖으로 안 나가게 반대로(왼쪽) 뒤집는다 */
.milestone-badge-side {
  transform: translate(10px, -50%);
}

.milestone-badge-side.milestone-badge-flip {
  transform: translate(calc(-100% - 10px), -50%);
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
  font-size: 8px;
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
