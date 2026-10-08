<script setup>
// 공통 레이아웃 - 사이드바 + 중앙 정렬된 본문 (넓은 화면에서 좌우 여백 균형, 모바일에서 사이드바는 드로어로 전환)
import { ref, computed, watch } from 'vue'
import Sidebar from './Sidebar.vue'
import ChatLauncher from './chat/ChatLauncher.vue'
import ChatPanel from './chat/ChatPanel.vue'
import PushModal from './community/PushModal.vue'
import { useUiStore } from '../stores/ui'
import { useFoldersStore } from '../stores/folders'
import AnnouncementPopup from './AnnouncementPopup.vue'
import { useChatStore } from '../stores/chat'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  // 기본 1040 - HomeView/AdminView와 동일한 폭. 다르게 하고 싶은 화면만 개별로 max-width를 넘기면 됨
  maxWidth: { type: Number, default: 1040 },
  // 사이드바 "홈" 버튼과 본문 시작 위치를 맞추기 위한 페이지별 상단 여백 - 필요할 때만 개별 조정
  paddingTop: { type: Number, default: 85 },
})

const sidebarOpen = ref(false)
// 데스크톱~태블릿 폭에서 사용자가 수동으로 사이드바를 접어 본문 폭을 넓힐 때 쓰는 상태 - 페이지 이동 간에도
// 유지되어야 해서 스토어에 둠 (모바일 드로어용 sidebarOpen과는 별개 - 768px 미만에서는 CSS가 이 상태를 무시함)
const uiStore = useUiStore()
// AI 챗봇은 1차 베타라 일부 반에게만 열려 있다. **누구에게 열지는 서버가 판단한다**
// (ai 서버 .env의 CHAT_ALLOWED_CAMPUS/CLASSES). 화면이 같은 규칙을 들고 있으면 서버 설정만
// 바꿨을 때 조용히 어긋나므로, 여기서는 물어보고 결과만 쓴다.
// AI 서버가 꺼져 있으면 allowed:false로 와서 버튼이 안 뜬다 - 눌러도 안 되는 버튼보다 낫다.
// 패널이 열리면 본문이 왼쪽으로 밀림 (chat-open 클래스)
const chatStore = useChatStore()
const authStore = useAuthStore()
const foldersStore = useFoldersStore()
const showChat = computed(() => chatStore.canUse === true)

// 로그인한 뒤에만 물어본다. 로그인 전에 부르면 401이라 의미가 없다.
watch(
  () => authStore.isAuthenticated,
  (yes) => {
    if (yes) {
      chatStore.loadAccess()
      foldersStore.load() // 내 폴더는 서버에 있다 - 로그인 후 한 번 불러온다
    } else chatStore.canUse = false
  },
  { immediate: true },
)
const innerStyle = computed(() => ({ maxWidth: `${props.maxWidth}px` }))
const mainStyle = computed(() => ({ paddingTop: `${props.paddingTop}px` }))

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function closeSidebar() {
  sidebarOpen.value = false
}
</script>

<template>
  <div
    class="app-layout"
    :class="{ 'chat-open': showChat && chatStore.isOpen, 'chat-resizing': chatStore.dragging }"
    :style="{ '--chat-w': `${chatStore.width}px` }"
  >
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="closeSidebar"></div>
    <div class="sidebar-wrap" :class="{ open: sidebarOpen, collapsed: uiStore.sidebarCollapsed }">
      <Sidebar @navigate="closeSidebar" @collapse="uiStore.collapseSidebar" />
    </div>
    <button class="menu-toggle" aria-label="메뉴" @click="toggleSidebar">»</button>
    <button
      v-if="uiStore.sidebarCollapsed"
      class="menu-toggle sidebar-expand-toggle"
      aria-label="사이드바 펼치기"
      @click="uiStore.expandSidebar"
    >
      »
    </button>
    <main class="app-main" :style="mainStyle">
      <div class="app-main-inner" :style="innerStyle">
        <slot />
      </div>
    </main>
    <!-- 안 읽은 공지 팝업. 챗봇과 달리 로그인한 모두에게 보여야 하므로 showChat 밖에 둔다 -->
    <AnnouncementPopup />

    <template v-if="showChat">
      <ChatLauncher />
      <ChatPanel />
      <PushModal />
    </template>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background: #fafafa;
  position: relative;
}

.menu-toggle {
  display: none;
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 200;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid rgba(26, 26, 46, 0.08);
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.1);
  font-size: 16px;
  cursor: pointer;
}

.sidebar-overlay {
  display: none;
}

/* .sidebar-expand-toggle는 .menu-toggle 클래스를 함께 써서 모양(위치/크기/스타일)을 그대로 공유하고,
   보이는 조건(display)만 여기서 따로 제어 - 접힘/펼침 버튼과 모바일 햄버거가 같은 아이콘(»)으로 통일되고,
   로고 옆 접기 버튼(«)과도 짝이 맞게 */
.sidebar-expand-toggle {
  display: none;
}

/* 900px 이상(데스크톱~태블릿)에서만 접기 기능이 의미가 있음 - 모바일은 기존 드로어(sidebarOpen)만 사용.
   사이드바가 고정 컬럼으로 있으면서 본문 폭을 너무 좁게 만드는 구간을 줄이기 위해 기존 768px보다
   더 넓은 화면에서부터 일찍 드로어(모바일) 방식으로 전환한다 */
@media (min-width: 900px) {
  .sidebar-wrap.collapsed {
    display: none;
  }

  .sidebar-expand-toggle {
    display: flex;
  }
}

/* 모바일 폭으로 리사이즈된 상태로 접힘 상태가 남아있어도, 실제 햄버거 버튼과 겹쳐 두 개가 뜨지 않게 항상 숨김 */
@media (max-width: 900px) {
  .sidebar-expand-toggle {
    display: none !important;
  }
}

.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
  /* padding-top은 :style="mainStyle"로 페이지별(paddingTop prop)로 넘어옴 */
  padding: 0 48px 72px;
  transition: margin-right 0.32s cubic-bezier(0.2, 0.8, 0.3, 1);
}

/* 챗봇 패널(폭 --chat-w, 드래그로 조절)이 열리면 본문이 그만큼 왼쪽으로 밀림 - 모바일(900px 이하)은 패널이 전체를 덮으므로 제외 */
@media (min-width: 901px) {
  .chat-open .app-main {
    margin-right: var(--chat-w);
  }

  .chat-resizing .app-main {
    transition: none; /* 드래그 중엔 즉시 따라오게 */
  }
}

.app-main-inner {
  width: 100%;
  min-width: 0;
  position: relative;
}

@media (max-width: 1024px) {
  .app-main {
    padding: 0 24px 56px;
    padding-top: 56px !important;
  }
}

@media (max-width: 900px) {
  .menu-toggle {
    display: flex;
  }

  .sidebar-wrap {
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    z-index: 150;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
    box-shadow: 0 0 24px rgba(0, 0, 0, 0.15);
  }

  .sidebar-wrap.open {
    transform: translateX(0);
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(26, 26, 46, 0.4);
    z-index: 140;
  }

  .app-main {
    padding: 0 16px 40px;
    padding-top: 68px !important;
  }
}
</style>
