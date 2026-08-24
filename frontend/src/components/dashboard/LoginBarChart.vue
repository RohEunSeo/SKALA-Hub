<script setup>
// 대시보드 - 가입자 분포 바차트 (v3.html PAGE 7 DASHBOARD 재현, 스크롤 진입 시 바가 차오름)
// 학생 반 막대끼리, 스태프(운영진/매니저/교수님) 막대끼리 각각 자기 그룹 안에서만 상대 높이를 매긴다
// (두 그룹은 인원 규모 자체가 달라서 같은 기준으로 비교하면 스태프 쪽이 항상 짧아 보임)
import { computed } from 'vue'
import { useHoverTooltip } from '../../composables/useHoverTooltip'

const props = defineProps({
  loginByClass: { type: Array, required: true }, // [{ label, count, isStaff }]
  revealed: { type: Boolean, default: false },
})

const STUDENT_COLOR = '#4A3F8F'
const STAFF_COLOR = '#C4B0F5'

const { hoveredKey, rect, onEnter, onLeave } = useHoverTooltip()

const studentMax = computed(() => {
  const counts = props.loginByClass.filter((c) => !c.isStaff).map((c) => c.count)
  return Math.max(1, ...counts, 0)
})

const staffMax = computed(() => {
  const counts = props.loginByClass.filter((c) => c.isStaff).map((c) => c.count)
  return Math.max(1, ...counts, 0)
})

function barHeightPct(c) {
  if (!props.revealed) return 0
  const groupMax = c.isStaff ? staffMax.value : studentMax.value
  return Math.round((c.count / groupMax) * 100)
}

const tooltipStyle = computed(() => {
  if (!rect.value) return {}
  return {
    left: rect.value.left + rect.value.width / 2 + 'px',
    top: rect.value.top - 8 + 'px',
    transform: 'translate(-50%, -100%)',
  }
})

const hoveredBar = computed(() => props.loginByClass.find((c) => c.label === hoveredKey.value))
</script>

<template>
  <div class="chart-card">
    <div class="chart-title">🎒 가입자 분포</div>
    <div class="chart-sub">막대에 마우스를 올리면 정확한 인원이 표시됩니다</div>
    <div class="bars-row">
      <div v-for="c in loginByClass" :key="c.label" class="bar-col">
        <div class="bar-track">
          <div
            class="bar-fill"
            :style="{ height: barHeightPct(c) + '%', background: c.isStaff ? STAFF_COLOR : STUDENT_COLOR }"
            @mouseenter="onEnter($event, c.label)"
            @mouseleave="onLeave"
          ></div>
        </div>
        <div class="bar-name">{{ c.label }}</div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="hoveredBar" class="bar-tooltip" :style="tooltipStyle">{{ hoveredBar.count }}명</div>
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
  margin-bottom: 18px;
}

.bars-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 150px;
  margin-top: 10px;
}

.bar-col {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}

.bar-track {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
}

.bar-fill {
  width: 100%;
  border-radius: 8px 8px 0 0;
  transition: height 1.5s cubic-bezier(0.22, 1, 0.36, 1);
  cursor: pointer;
}

.bar-name {
  margin-top: 6px;
  font-size: 9.5px;
  font-weight: 600;
  color: #636e72;
  white-space: nowrap;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-tooltip {
  position: fixed;
  background: #1a1a2e;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 5px 8px;
  border-radius: 7px;
  white-space: nowrap;
  z-index: 1000;
  pointer-events: none;
}
</style>
