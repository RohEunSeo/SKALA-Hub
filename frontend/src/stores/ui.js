// 화면 전반 레이아웃 상태 - 사이드바 접힘 여부 등 (페이지 이동에도 유지되어야 해서 컴포넌트 로컬 상태 대신 스토어에 둠)
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  // 데스크톱~태블릿 폭에서 사용자가 수동으로 사이드바를 접었는지 - 새로고침 시엔 초기화되고, 같은 세션 내
  // 페이지 이동(라우팅) 간에는 유지됨
  const sidebarCollapsed = ref(false)

  // 피드 화면 상단 탭('posts' | 'links' | 'curriculum') - FeedView가 로컬로 들고 있던 값을 사이드바
  // "링크 모음 / SKALA 커리큘럼" 하위 메뉴와 공유하기 위해 스토어에 동기화 (FeedView가 유일한 갱신 주체,
  // 사이드바가 피드 화면에서 값을 바꾸면 FeedView가 감시하다가 탭을 전환)
  const feedTab = ref('posts')

  function collapseSidebar() {
    sidebarCollapsed.value = true
  }

  function expandSidebar() {
    sidebarCollapsed.value = false
  }

  return { sidebarCollapsed, feedTab, collapseSidebar, expandSidebar }
})
