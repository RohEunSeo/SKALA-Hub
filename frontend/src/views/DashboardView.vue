<script setup>
// 대시보드 - 사이드바 전용 메뉴 (로그인한 사용자면 누구나, v3.html PAGE 7 DASHBOARD 재현. 지식 그래프 섹션은 범위 제외)
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import DashboardPanel from '../components/dashboard/DashboardPanel.vue'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <AppLayout :max-width="1200">
    <AuthRequired v-if="!authStore.isAuthenticated" message="대시보드는 로그인이 필요합니다" />
    <DashboardPanel v-else />
  </AppLayout>
</template>
