<script setup>
// 대시보드 - 게시글 기여도 히트맵 (GitHub 잔디 스타일, v3.html PAGE 7 DASHBOARD 재현)
import { computed } from 'vue'
import { useIsMobile } from '../../composables/useIsMobile'
import { useHoverTooltip } from '../../composables/useHoverTooltip'

const props = defineProps({
  heatmap: { type: Array, required: true }, // [{ date: 'YYYY-MM-DD', count, level }] - 교육 기간 전체(courseStart~courseEnd), 미래 날짜는 level 0으로 내려옴
  stats: { type: Object, required: true }, // { maxDayLabel, maxDayCount, avgPerDay, bestWeekday, streakDays }
})

// 깃허브 잔디와 동일한 5단계(0=없음 + 4단계 초록) 팔레트
const LEVEL_COLORS = ['#EBEDF0', '#9BE9A8', '#40C463', '#30A14E', '#216E39']

const isMobile = useIsMobile()
const { hoveredKey, rect, onEnter, onLeave } = useHoverTooltip()

function parseDate(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(y, m - 1, d)
}

// 주(일~토) 단위 컬럼으로 재구성 - 시작 주만 빈 칸으로 패딩
const weeks = computed(() => {
  if (!props.heatmap.length) return []
  const padStart = parseDate(props.heatmap[0].date).getDay()
  const cells = [...Array(padStart).fill(null), ...props.heatmap]
  while (cells.length % 7 !== 0) cells.push(null)
  const result = []
  for (let i = 0; i < cells.length; i += 7) result.push(cells.slice(i, i + 7))
  return result
})

// 데스크탑용 - 그 주에 "1일"이 실제로 포함된 칸의 바로 다음 칸에 월 라벨을 달아서(깃허브처럼
// 라벨이 그 달 칸에 딱 붙지 않고 한 칸 뒤에 오도록), 시작 칸 바로 위에 있을 때보다 자연스럽게 보이게 함.
// 다만 첫 주는 교육 기간이 월 중간(7/14)에 시작해 1일이 없으므로, 그 주의 첫 유효한 날짜를 시작월로 봄
const monthStarts = computed(() =>
  weeks.value.map((week, wi) => {
    const target = week.find((d) => d && d.date.slice(8, 10) === '01') ?? (wi === 0 ? week.find((d) => d) : null)
    return target ? Number(target.date.slice(5, 7)) : null
  }),
)

const weeksWithLabel = computed(() => {
  const starts = monthStarts.value
  return weeks.value.map((week, wi) => {
    // 11월만 다음 칸이 아니라 원래 칸(1일이 포함된 칸)에 표시 - 다른 달과 한 칸 어긋나 보여서 예외 처리
    let monthNum = null
    if (starts[wi] === 11) {
      monthNum = 11
    } else if (wi > 0 && starts[wi - 1] && starts[wi - 1] !== 11) {
      monthNum = starts[wi - 1]
    }
    return { monthLabel: monthNum ? monthNum + '월' : '', days: week }
  })
})

function tipText(day) {
  const monthNum = Number(day.date.slice(5, 7))
  const dayNum = Number(day.date.slice(8, 10))
  return `${monthNum}월 ${dayNum}일 · ${day.count}개 게시글`
}

const tooltipStyle = computed(() => {
  if (!rect.value) return {}
  return {
    left: rect.value.right + 8 + 'px',
    top: rect.value.top + rect.value.height / 2 + 'px',
    transform: 'translateY(-50%)',
  }
})

// 깃허브의 "N contributions in the last year"처럼 왼쪽 상단에 총 게시글 수를 영어로 표시
const totalContributions = computed(() => props.heatmap.reduce((sum, d) => sum + d.count, 0))
</script>

<template>
  <div class="heatmap-section">
    <div class="heatmap-header">
      <div class="heatmap-title">🌿 게시글 기여도</div>
      <div class="heatmap-subtitle">{{ totalContributions }} contributions in this year</div>
    </div>

    <div class="heatmap-body">
      <div class="weeks-area">
        <div class="weeks-track" :style="{ gridTemplateColumns: `repeat(${weeks.length}, 1fr)` }">
          <div v-for="(week, wi) in weeksWithLabel" :key="wi" class="week-col">
            <div v-if="!isMobile" class="month-label">{{ week.monthLabel }}</div>
            <div
              v-for="(day, di) in week.days"
              :key="di"
              class="cell-wrap"
              @mouseenter="day && onEnter($event, day.date)"
              @mouseleave="onLeave"
            >
              <div
                class="cell"
                :style="{
                  background: day ? LEVEL_COLORS[day.level] : 'transparent',
                  animationDelay: day ? wi * 0.02 + 's' : null,
                }"
              ></div>
            </div>
          </div>
        </div>
        <div class="legend">
          Less
          <span v-for="(color, i) in LEVEL_COLORS" :key="i" class="legend-swatch" :style="{ background: color }"></span>
          More
        </div>
      </div>

      <div class="stats-side">
        <div>
          <div class="stat-label">🔥 최고 기록일</div>
          <div class="stat-value">{{ stats.maxDayLabel }} <span class="stat-sub">· {{ stats.maxDayCount }}개</span></div>
        </div>
        <div>
          <div class="stat-label">📊 하루 평균</div>
          <div class="stat-value">{{ stats.avgPerDay.toFixed(1) }}개</div>
        </div>
        <div>
          <div class="stat-label">📅 가장 활발한 요일</div>
          <div class="stat-value">{{ stats.bestWeekday }}</div>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="hoveredKey" class="cell-tooltip" :style="tooltipStyle">
        {{ tipText(heatmap.find((d) => d.date === hoveredKey)) }}
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
@keyframes cell-pop {
  from {
    transform: scale(0.3);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

/* container-type:inline-size로 "이 카드 자체의" 실제 렌더링 폭을 기준으로 잔디/통계를 세로로
   쌓을지 정한다(옆 나무 카드가 폭을 얼마나 차지하는지와 무관하게, 히트맵 쪽이 실제로 좁아지는
   시점에 바로 반응). growth-card의 align-items는 stretch가 아니라 flex-start라서, 이 카드가
   내부적으로 세로로 쌓여 키가 커져도 옆 나무 카드가 그 높이에 억지로 안 맞춰진다 */
.heatmap-section {
  container-type: inline-size;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
}

.heatmap-header {
  margin-top: 0;
  margin-bottom: 4px;
}

/* line-height를 1 대신 고정 px로 - 이모지 글자마다 폰트에 내장된 세로 메트릭이 미묘하게 달라서
   line-height:1(폰트 자체 기준)로는 "허브 단계" 제목과 몇 px씩 어긋났다. 고정값이면 이모지 종류와
   무관하게 두 제목의 줄 상자 높이가 항상 완전히 동일해진다 */
.heatmap-title {
  font-size: 15px;
  font-weight: 800;
  line-height: 18px;
  color: #1a1a2e;
}

.heatmap-subtitle {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #4a4f57;
}

/* 실제 깃허브처럼 잔디 영역 오른쪽 아래에 둔다 */
.legend {
  margin-top: 8px;
  font-size: 11px;
  color: #636e72;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
}

.legend-swatch {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.heatmap-body {
  display: flex;
  gap: 40px;
  align-items: flex-start;
}

/* 가로 스크롤 없이 교육 기간 전체(오늘 이후 빈 칸 포함)가 항상 다 들어오는 유동형 그리드.
   폭에 상한을 둬서 통계 블록이 바로 옆에 붙게 하되, 화면이 좁아지면 자연스럽게 같이 줄어든다.
   칸이 정사각형(aspect-ratio:1)이라 이 폭을 줄이면 세로 높이도 같은 비율로 같이 줄어든다 */
.weeks-area {
  flex: 0 1 470px;
  min-width: 0;
}

.weeks-track {
  display: grid;
  gap: 3px;
}

.week-col {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.month-label {
  height: 13px;
  font-size: 10px;
  color: #a0a4ac;
  white-space: nowrap;
}

.cell-wrap {
  position: relative;
}

.cell {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 3px;
  animation: cell-pop 0.35s ease both;
}

.cell-tooltip {
  position: fixed;
  background: #1a1a2e;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 5px 9px;
  border-radius: 7px;
  white-space: nowrap;
  z-index: 1000;
  pointer-events: none;
}

.stats-side {
  width: 150px;
  flex-shrink: 0;
  border-left: 1px solid #f0f0f2;
  padding-left: 14px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  justify-content: center;
}

.stat-label {
  font-size: 11px;
  color: #a0a4ac;
  margin-bottom: 3px;
}

.stat-value {
  font-size: 14px;
  font-weight: 800;
  color: #1a1a2e;
}

.stat-sub {
  color: #636e72;
  font-weight: 600;
  font-size: 12px;
}

/* 잔디(weeks-area 최대 470px) + gap(40px) + 통계 사이드(150px) = 약 660px가 여유 있게 다 들어가는 폭.
   이 카드 자체가 그보다 조금이라도 좁아지려는 순간(옆 나무 카드가 얼마나 넓든 상관없이) 바로 세로
   스택으로 바꿔서, 잔디 칸이 눌려서 작아지는 게 눈에 보이기 전에 항상 미리 전환되게 한다.
   나무 카드가 옆에서 폭을 차지해 growth-card 전체 뷰포트 브레이크포인트(768px)보다 이게 먼저 발동해도,
   growth-card의 align-items가 stretch가 아니라 flex-start라서 나무 카드가 억지로 늘어나지 않는다 */
@container (max-width: 670px) {
  .heatmap-body {
    flex-direction: column;
  }

  .weeks-area {
    flex: 1 1 auto;
    width: 100%;
  }

  .stats-side {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: flex-start;
    gap: 24px;
    border-left: none;
    border-top: 1px solid #f0f0f2;
    padding-left: 0;
    padding-top: 16px;
    margin-top: 4px;
  }
}
</style>
