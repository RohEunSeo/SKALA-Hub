// 화면 전반 레이아웃 상태 - 사이드바 접힘 여부 등 (페이지 이동에도 유지되어야 해서 컴포넌트 로컬 상태 대신 스토어에 둠)
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  // 데스크톱~태블릿 폭에서 사용자가 수동으로 사이드바를 접었는지 - 새로고침 시엔 초기화되고, 같은 세션 내
  // 페이지 이동(라우팅) 간에는 유지됨
  const sidebarCollapsed = ref(false)

  function collapseSidebar() {
    sidebarCollapsed.value = true
  }

  function expandSidebar() {
    sidebarCollapsed.value = false
  }

  return { sidebarCollapsed, collapseSidebar, expandSidebar }
})
