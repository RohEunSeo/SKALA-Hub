<script setup>
// 홈 상단바 - 관리자 계정에게만 보이는 관리자 모드 / 일반 모드 스와이핑 토글.
// 일반 모드로 전환하면 effectiveIsAdmin이 false가 되어 관리자 전용 UI가 전부 숨겨짐(실제 권한은 유지).
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <button
    v-if="authStore.isAdmin"
    type="button"
    class="view-mode-toggle"
    :class="{ 'is-user': authStore.viewMode === 'user' }"
    :title="authStore.viewMode === 'admin' ? '일반 모드로 전환 (학생 화면 미리보기)' : '관리자 모드로 전환'"
    @click="authStore.toggleViewMode()"
  >
    <span class="view-mode-track">
      <span class="view-mode-thumb">{{ authStore.viewMode === 'admin' ? '🛡️' : '👤' }}</span>
    </span>
  </button>
</template>

<style scoped>
.view-mode-toggle {
  flex-shrink: 0;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
}

.view-mode-track {
  position: relative;
  display: block;
  width: 56px;
  height: 30px;
  border-radius: 999px;
  background: #e3ddf7;
  transition: background 0.2s ease;
}

.view-mode-toggle.is-user .view-mode-track {
  background: #e8eaed;
}

.view-mode-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.25);
  transition: left 0.2s ease;
}

.view-mode-toggle.is-user .view-mode-thumb {
  left: 28px;
}
</style>
