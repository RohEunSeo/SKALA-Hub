<script setup>
// 화면 하단 토스트 알림 - App.vue에 한 번만 마운트
import { useToastStore } from '../stores/toast'

const toastStore = useToastStore()
</script>

<template>
  <Transition name="toast-fade">
    <div v-if="toastStore.visible" class="toast">
      <span class="toast-message">{{ toastStore.message }}</span>
      <span v-if="toastStore.actionLabel" class="toast-action" @click="toastStore.triggerAction">
        {{ toastStore.actionLabel }}
      </span>
      <span class="toast-close" @click="toastStore.hide">✕</span>
    </div>
  </Transition>
</template>

<style scoped>
.toast {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  background: #1a1a2e;
  color: #fff;
  padding: 14px 20px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  z-index: 1000;
  font-size: 13.5px;
  white-space: nowrap;
}

.toast-action {
  color: #a8b4ff;
  font-weight: 700;
  cursor: pointer;
}

.toast-close {
  cursor: pointer;
  opacity: 0.6;
  font-size: 12px;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.2s ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
}
</style>
