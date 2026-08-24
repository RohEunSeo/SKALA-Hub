<script setup>
// 대시보드 - 게시글 기여도 히트맵 (GitHub 잔디 스타일, v3.html PAGE 7 DASHBOARD 재현)
import { computed } from 'vue'
import { useIsMobile } from '../../composables/useIsMobile'
import { useHoverTooltip } from '../../composables/useHoverTooltip'

const props = defineProps({
  heatmap: { type: Array, required: true }, // [{ date: 'YYYY-MM-DD', count, level }] - 오늘까지만 내려옴
  stats: { type: Object, required: true }, // { maxDayLabel, maxDayCount, avgPerDay, bestWeekday, streakDays }
})

const LEVEL_COLORS = ['#EBEDF0', '#7FD48B', '#3FAE5B', '#1E7A38']

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

// 데스크탑용 - 주 중간(4번째 요일) 기준으로 달이 바뀌는 시점에만 월 라벨 표시
const weeksWithLabel = computed(() => {
  let prevMonth = null
  return weeks.value.map((week) => {
    const mid = week[3]
    let monthLabel = ''
    if (mid) {
      const monthNum = Number(mid.date.slice(5, 7))
      if (monthNum !== prevMonth) {
        monthLabel = monthNum + '월'
        prevMonth = monthNum
      }
    }
    return { monthLabel, days: week }
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
</script>

<template>
  <div class="heatmap-section">
    <div class="heatmap-header">
      <span class="heatmap-title">🌿 게시글 기여도</span>
      <div class="legend">
        Less
        <span v-for="(color, i) in LEVEL_COLORS" :key="i" class="legend-swatch" :style="{ background: color }"></span>
        More
      </div>
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

/* 옆 나무 카드보다 콘텐츠가 짧아서 growth-card 높이(align-items:stretch)만큼 늘어났을 때,
   위쪽에만 붙지 않고 그 늘어난 높이 안에서 위아래로 고르게 배치되도록 flex column + center */
.heatmap-section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.heatmap-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 6px;
}

.heatmap-title {
  font-size: 15px;
  font-weight: 800;
  color: #1a1a2e;
}

.legend {
  font-size: 11px;
  color: #636e72;
  display: flex;
  align-items: center;
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
  gap: 28px;
  align-items: flex-start;
}

/* 가로 스크롤 없이 교육 기간 전체(오늘 이후 빈 칸 포함)가 항상 다 들어오는 유동형 그리드.
   폭에 상한을 둬서 통계 블록이 바로 옆에 붙게 하되, 화면이 좁아지면 자연스럽게 같이 줄어든다 */
.weeks-area {
  flex: 0 1 540px;
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

/* 화면이 좁아지면 옆이 아니라 그리드 아래로 내려서 가로 한 줄로 배치 (항목은 데스크탑과 동일하게 3개 유지) */
@media (max-width: 768px) {
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
