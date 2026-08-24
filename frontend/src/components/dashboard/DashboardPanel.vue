<script setup>
// 마이페이지 "대시보드" 탭 (관리자 전용) - v3.html PAGE 7 DASHBOARD를 실제 데이터로 재현
// 지식 그래프 섹션은 범위 제외
import { computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useDashboardStore } from '../../stores/dashboard'
import SkeletonBlock from '../SkeletonBlock.vue'
import TreeGrowthCard from './TreeGrowthCard.vue'
import ContributionHeatmap from './ContributionHeatmap.vue'
import HallOfFamePodium from './HallOfFamePodium.vue'
import StatsRow from './StatsRow.vue'

const dashboardStore = useDashboardStore()
const { summary, loading, error } = storeToRefs(dashboardStore)

onMounted(() => dashboardStore.startPolling())
onUnmounted(() => dashboardStore.stopPolling())

function formatDate(dateStr) {
  const [y, m, d] = dateStr.split('-')
  return `${y}.${m}.${d}`
}

const dateRangeLabel = computed(() => {
  if (!summary.value) return ''
  return `${formatDate(summary.value.courseStart)} ~ ${formatDate(summary.value.courseEnd)} · 교육 기간 전체 활동 기록`
})
</script>

<template>
  <div class="dashboard-panel">
    <div v-if="!summary && loading" class="dashboard-skeleton" aria-hidden="true">
      <SkeletonBlock width="240px" height="24px" />
      <SkeletonBlock width="100%" height="180px" radius="16px" />
      <SkeletonBlock width="100%" height="240px" radius="16px" />
    </div>
    <div v-else-if="!summary && error" class="empty">{{ error }}</div>
    <template v-else-if="summary">
      <div class="dashboard-header">
        <div class="dashboard-title-row">
          <div class="dashboard-title">함께 키운 SKALA Hub 🌱</div>
          <div class="dday-badge">D-{{ summary.daysLeft }} 남음</div>
        </div>
        <div class="dashboard-subtitle">{{ dateRangeLabel }}</div>
      </div>

      <div class="growth-card">
        <TreeGrowthCard :tree-stage="summary.treeStage" />
        <ContributionHeatmap :heatmap="summary.heatmap" :stats="summary.heatmapStats" />
      </div>

      <HallOfFamePodium :hall-of-fame="summary.hallOfFame" />

      <StatsRow
        :signup-trend="summary.signupTrend"
        :login-by-class="summary.loginByClass"
        :category-dist="summary.categoryDist"
      />
    </template>
  </div>
</template>

<style scoped>
@keyframes fade-up {
  from {
    transform: translateY(10px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.dashboard-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty {
  padding: 60px 0;
  text-align: center;
  color: #636e72;
  font-size: 14px;
}

.dashboard-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dashboard-title {
  font-size: 24px;
  font-weight: 800;
  color: #1a1a2e;
}

.dashboard-subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: #636e72;
}

.dday-badge {
  font-size: 12.5px;
  font-weight: 700;
  color: #4a3f8f;
  background: #f1eefc;
  padding: 8px 14px;
  border-radius: 999px;
  white-space: nowrap;
}

.growth-card {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 1px 5px rgba(26, 26, 46, 0.05);
  padding: 36px 24px 24px 36px;
  display: flex;
  gap: 40px;
  align-items: stretch;
  animation: fade-up 0.5s ease both;
}

@media (max-width: 768px) {
  .dashboard-title-row {
    flex-wrap: wrap;
    gap: 8px;
  }

  .growth-card {
    flex-direction: column;
    padding: 24px;
    gap: 20px;
  }
}
</style>
