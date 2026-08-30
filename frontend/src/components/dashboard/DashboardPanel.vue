<script setup>
// 대시보드 탭 (로그인한 사용자면 누구나) - v3.html PAGE 7 DASHBOARD를 실제 데이터로 재현
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
        <div class="dashboard-title">함께 키운 SKALA Hub 🌱</div>
        <div class="dashboard-subtitle">{{ dateRangeLabel }}</div>
      </div>

      <div class="growth-card">
        <div class="dday-badge">D-{{ summary.daysLeft }} 남음</div>
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

/* growth-card 안, 오른쪽 위 구석에 박스 안쪽으로 들어오게 절대배치 */
.dday-badge {
  position: absolute;
  top: 20px;
  right: 24px;
  font-size: 12.5px;
  font-weight: 700;
  color: #4a3f8f;
  background: #f1eefc;
  padding: 8px 14px;
  border-radius: 999px;
  white-space: nowrap;
}

/* align-items:flex-start(stretch 아님) - 안쪽 히트맵 카드가 자기 폭 기준 컨테이너 쿼리로
   내부적으로 세로 스택되어 키가 커져도, 옆 나무 카드가 그 늘어난 높이에 억지로 맞춰 늘어나지 않는다 */
.growth-card {
  position: relative;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 1px 5px rgba(26, 26, 46, 0.05);
  padding: 26px 24px 22px 32px;
  display: flex;
  gap: 32px;
  align-items: flex-start;
  animation: fade-up 0.5s ease both;
}

@media (max-width: 768px) {
  .dday-badge {
    top: 16px;
    right: 16px;
  }

  /* flex-direction이 column이 되면 align-items는 이제 "가로 폭"을 통제한다(세로일 땐 높이를 통제).
     데스크탑의 flex-start는 거기선 옳지만, 여기서 그대로 두면 나무 카드/히트맵 카드가 폭에 맞춰
     안 늘어나고 내용만큼만 좁게 쪼그라들어서(특히 container-type:inline-size가 걸린 히트맵 카드는
     최소 폭 안전장치가 없어져서 거의 0폭까지 줄어듦) 제목이 세로로 한 글자씩 줄바꿈되는 것처럼 보였다.
     세로로 쌓일 땐 stretch로 각 카드가 항상 전체 폭을 꽉 채우게 한다 */
  .growth-card {
    flex-direction: column;
    align-items: stretch;
    padding: 24px;
    gap: 20px;
  }
}
</style>
