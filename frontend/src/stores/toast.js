// 화면 하단 토스트 알림 (저장하기 등 액션 피드백)
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const message = ref('')
  const actionLabel = ref('')
  const actionFn = ref(null)
  const visible = ref(false)
  let hideTimer = null

  function show(newMessage, { actionLabel: label = '', onAction = null, duration = 4000 } = {}) {
    message.value = newMessage
    actionLabel.value = label
    actionFn.value = onAction
    visible.value = true
    clearTimeout(hideTimer)
    hideTimer = setTimeout(hide, duration)
  }

  function hide() {
    visible.value = false
  }

  function triggerAction() {
    actionFn.value?.()
    hide()
  }

  return { message, actionLabel, visible, show, hide, triggerAction }
})
