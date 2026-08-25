<script setup>
// 대시보드 - 누적 게시글 수 기준 나무 성장 카드 (v3.html PAGE 7 DASHBOARD 나무 섹션 재현)
import { computed, onMounted, onUnmounted } from 'vue'
import { useWeatherStore } from '../../stores/weather'
import WeatherWidgetBg from './WeatherWidgetBg.vue'

const weatherStore = useWeatherStore()
onMounted(() => weatherStore.startPolling())
onUnmounted(() => weatherStore.stopPolling())

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

const WEATHER_LABELS = {
  sunny: '☀️ 맑음',
  cloudy: '☁️ 흐림',
  rainy: '🌧️ 비',
  snowy: '❄️ 눈',
  foggy: '🌫️ 안개',
  stormy: '⛈️ 뇌우',
}

const weatherLine = computed(() => {
  if (!weatherStore.condition || weatherStore.temperature == null) return ''
  const label = WEATHER_LABELS[weatherStore.condition] ?? '날씨'
  return `${label} · ${weatherStore.temperature}°`
})

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
    <div class="tree-bg-layer">
      <WeatherWidgetBg :condition="weatherStore.condition" :is-day="weatherStore.isDay" />
    </div>

    <div class="tree-content">
      <div class="tree-title">🌱 허브 단계</div>
      <div v-if="weatherLine" class="tree-weather-line">{{ weatherLine }}</div>
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
   중앙정렬된 채 절반쯤 삐져나와도 구분선(border-right)과 안 겹칠 만큼 넉넉하게 둔다.
   overflow는 hidden으로 두지 않는다 - 씨앗(맨 왼쪽 눈금)이 절반 삐져나오게 배치돼 있어서
   여기서 자르면 잘려 보인다. 날씨 배경(WeatherWidgetBg)은 그 컴포넌트 자체에서 이미 클리핑한다 */
.tree-card {
  position: relative;
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

/* 오늘 판교 실시간 날씨(WeatherWidgetBg) - 사각형 색 배경 없이 해/구름/비/눈 같은 개별 그림 요소만
   카드의 원래 흰 배경 위에 얹히므로, 박스 형태의 경계가 생기지 않는다 (그래서 투명도도 거의 그대로) */
.tree-bg-layer {
  position: absolute;
  inset: 0;
  opacity: 0.9;
}

.tree-content {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tree-title {
  align-self: flex-start;
  font-size: 15px;
  font-weight: 800;
  line-height: 18px;
  color: #1a1a2e;
}

.tree-weather-line {
  align-self: flex-start;
  margin-top: 3px;
  margin-bottom: 11px;
  font-size: 11px;
  font-weight: 600;
  color: #8a8fa0;
}

.tree-emoji {
  font-size: 68px;
  line-height: 1;
  margin-top: 10px;
  animation:
    tree-grow 0.7s ease both,
    tree-sway 3s ease-in-out infinite;
  transform-origin: bottom center;
}

.tree-label {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}

/* 진행률 바 위쪽에 뜨는 0/100/200/300/400/500 숫자 줄 - 바 아래는 이모지·이름만 있어 복잡하지 않게 */
.tick-numbers {
  position: relative;
  width: 100%;
  height: 12px;
  margin-top: 12px;
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
  height: 32px;
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
  font-size: 13px;
}

.tick-label {
  font-size: 8px;
  color: #636e72;
  font-weight: 700;
  white-space: nowrap;
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
