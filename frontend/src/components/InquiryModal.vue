<script setup>
// 문의하기 모달 - 자체 폼 대신 기존에 운영 중인 Tally 폼(문의 유형/내용/만족도 별점 포함)을 iframe으로 임베드
import { onMounted, onUnmounted, ref } from 'vue'

const emit = defineEmits(['close'])
const panelRef = ref(null)

const TALLY_EMBED_URL =
  'https://tally.so/embed/gDX4G4?hideTitle=1&transparentBackground=1&dynamicHeight=1'

function close() {
  emit('close')
}

function handleKeydown(event) {
  if (event.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  panelRef.value?.querySelector('button')?.focus()
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="modal-overlay" @click.self="close">
      <div ref="panelRef" class="modal-panel" role="dialog" aria-modal="true" aria-label="문의하기">
        <button class="modal-close" aria-label="닫기" @click="close">✕</button>
        <div class="modal-iframe-wrap">
          <iframe
            class="modal-iframe"
            :src="TALLY_EMBED_URL"
            width="100%"
            height="560"
            frameborder="0"
            title="문의하기"
          ></iframe>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 26, 46, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.modal-panel {
  position: relative;
  width: 100%;
  max-width: 620px;
  max-height: 90vh;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(26, 26, 46, 0.25);
  overflow-y: auto;
}

.modal-iframe-wrap {
  padding: 40px 28px 24px;
}

.modal-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: none;
  background: rgba(26, 26, 46, 0.06);
  color: #1a1a2e;
  font-size: 14px;
  cursor: pointer;
  z-index: 1;
}

.modal-close:hover {
  background: rgba(26, 26, 46, 0.12);
}

.modal-iframe {
  display: block;
  border: none;
}

@media (max-width: 640px) {
  .modal-panel {
    max-width: 100%;
  }
}
</style>
