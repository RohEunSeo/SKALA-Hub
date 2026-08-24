// 대시보드 등 반응형 레이아웃 분기용 - MyPageView.vue가 이미 쓰는 768px 브레이크포인트와 통일
import { onMounted, onUnmounted, ref } from 'vue'

const BREAKPOINT = '(max-width: 768px)'

export function useIsMobile() {
  const isMobile = ref(false)
  let mql = null
  let handler = null

  onMounted(() => {
    mql = window.matchMedia(BREAKPOINT)
    isMobile.value = mql.matches
    handler = (e) => {
      isMobile.value = e.matches
    }
    mql.addEventListener('change', handler)
  })

  onUnmounted(() => {
    mql?.removeEventListener('change', handler)
  })

  return isMobile
}
