<script setup>
// 대시보드 - 사이드바 전용 메뉴 (관리자 전용, v3.html PAGE 7 DASHBOARD 재현. 지식 그래프 섹션은 범위 제외)
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import DashboardPanel from '../components/dashboard/DashboardPanel.vue'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <AppLayout :max-width="1200">
    <AuthRequired v-if="!authStore.isAuthenticated" message="대시보드는 로그인이 필요합니다" />
    <div v-else-if="!authStore.effectiveIsAdmin" class="no-permission">
      <div class="no-permission-icon">🚫</div>
      <div class="no-permission-message">관리자 권한이 없습니다.</div>
    </div>
    <DashboardPanel v-else />
  </AppLayout>
</template>

<style scoped>
.no-permission {
  background: #ffffff;
  border-radius: 16px;
  padding: 56px 32px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.no-permission-icon {
  font-size: 32px;
  margin-bottom: 14px;
}

.no-permission-message {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
}
</style>
