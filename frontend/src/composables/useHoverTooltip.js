// 차트 호버 툴팁 공통 로직 - 부모의 overflow:auto/hidden에 잘리지 않도록 <Teleport to="body">와 함께 쓴다.
// 대상 엘리먼트의 실제 렌더링 좌표(rect)를 잡아뒀다가, 화면 기준 고정 위치로 툴팁을 띄운다.
import { ref } from 'vue'

export function useHoverTooltip() {
  const hoveredKey = ref(null)
  const rect = ref(null)

  function onEnter(event, key) {
    hoveredKey.value = key
    rect.value = event.currentTarget.getBoundingClientRect()
  }

  function onLeave() {
    hoveredKey.value = null
    rect.value = null
  }

  return { hoveredKey, rect, onEnter, onLeave }
}
