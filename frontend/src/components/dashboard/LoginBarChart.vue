<script setup>
// 대시보드 - 가입자 분포 바차트 (v3.html PAGE 7 DASHBOARD 재현, 스크롤 진입 시 바가 차오름)
// 모든 막대를 하나의 기준(전체 최대 인원)으로 스케일링해서, 막대 높이가 실제 인원수 차이를 그대로 반영한다
import { computed } from 'vue'
import { useHoverTooltip } from '../../composables/useHoverTooltip'

const props = defineProps({
  loginByClass: { type: Array, required: true }, // [{ label, count, isStaff }]
  revealed: { type: Boolean, default: false },
})

const STUDENT_COLOR = '#4A3F8F'
const STAFF_COLOR = '#C4B0F5'

const { hoveredKey, rect, onEnter, onLeave } = useHoverTooltip()

const overallMax = computed(() => Math.max(1, ...props.loginByClass.map((c) => c.count)))

function barHeightPct(c) {
  if (!props.revealed) return 0
  return Math.round((c.count / overallMax.value) * 100)
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
  gap: 4px;
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

/* 잘리는 대신 필요하면 2줄로 줄바꿈해서, 어떤 화면 폭에서도 글자가 잘리지 않고 전부 보이게 한다 */
.bar-name {
  margin-top: 6px;
  font-size: 9px;
  font-weight: 600;
  color: #636e72;
  letter-spacing: -0.3px;
  line-height: 1.2;
  white-space: normal;
  word-break: keep-all;
  max-width: 100%;
  text-align: center;
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
