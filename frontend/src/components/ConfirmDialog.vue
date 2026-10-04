<script setup>
// 되돌릴 수 없는 동작을 확인받는 공용 다이얼로그.
// 브라우저 기본 confirm()은 꾸밀 수 없고 문구가 투박해서 대체한다.
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  message: { type: String, default: '' },
  // 되돌릴 수 없는 동작이면 빨간 버튼 + 경고 문구
  danger: { type: Boolean, default: true },
  confirmLabel: { type: String, default: '삭제' },
  cancelLabel: { type: String, default: '취소' },
})

const emit = defineEmits(['confirm', 'cancel'])
const panel = ref(null)

function onKey(e) {
  if (!props.open) return
  if (e.key === 'Escape') emit('cancel')
}

onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))

// 열릴 때 취소에 포커스를 둬서 Enter로 실수 삭제하는 일을 막는다
watch(
  () => props.open,
  (isOpen) => isOpen && requestAnimationFrame(() => panel.value?.querySelector('.cd-cancel')?.focus()),
)
</script>

<template>
  <Transition name="cd">
    <div v-if="open" class="cd-overlay" @click.self="emit('cancel')">
      <div ref="panel" class="cd-panel" role="alertdialog" aria-modal="true" :aria-label="title">
        <h2 class="cd-title">{{ title }}</h2>
        <p v-if="message" class="cd-message">{{ message }}</p>
        <p v-if="danger" class="cd-warn">이 작업은 되돌릴 수 없습니다.</p>

        <div class="cd-actions">
          <button class="cd-cancel" @click="emit('cancel')">{{ cancelLabel }}</button>
          <button class="cd-confirm" :class="{ danger }" @click="emit('confirm')">{{ confirmLabel }}</button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.cd-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(26, 26, 46, 0.42);
}

.cd-panel {
  width: 100%;
  max-width: 360px;
  padding: 26px 26px 20px;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 44px rgba(26, 26, 46, 0.26);
}

.cd-title {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: #1a1a2e;
}

.cd-message {
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: #636e72;
  word-break: keep-all;
}

.cd-warn {
  margin: 12px 0 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: #fdeef1;
  font-size: 12.5px;
  font-weight: 600;
  color: #e01e5a;
}

.cd-actions {
  display: flex;
  gap: 8px;
  margin-top: 22px;
}

.cd-actions button {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font: inherit;
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease;
}

.cd-cancel {
  border: 1px solid rgba(26, 26, 46, 0.14);
  background: #ffffff;
  color: #1a1a2e;
}

.cd-cancel:hover {
  background: rgba(26, 26, 46, 0.04);
}

.cd-confirm {
  border: 0;
  background: #4a3f8f;
  color: #ffffff;
}

.cd-confirm.danger {
  background: #e01e5a;
}

.cd-confirm:hover {
  filter: brightness(0.94);
}

.cd-enter-active,
.cd-leave-active {
  transition: opacity 0.18s ease;
}

.cd-enter-active .cd-panel,
.cd-leave-active .cd-panel {
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.cd-enter-from,
.cd-leave-to {
  opacity: 0;
}

.cd-enter-from .cd-panel {
  transform: scale(0.94);
}

@media (prefers-reduced-motion: reduce) {
  .cd-enter-active,
  .cd-leave-active,
  .cd-enter-active .cd-panel,
  .cd-leave-active .cd-panel {
    transition: none;
  }
}
</style>
