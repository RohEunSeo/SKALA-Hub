<script setup>
// 공통 레이아웃 - 사이드바 + 중앙 정렬된 본문 (넓은 화면에서 좌우 여백 균형, 모바일에서 사이드바는 드로어로 전환)
import { ref, computed } from 'vue'
import Sidebar from './Sidebar.vue'

const props = defineProps({
  maxWidth: { type: Number, default: 900 },
})

const sidebarOpen = ref(false)
const innerStyle = computed(() => ({ maxWidth: `${props.maxWidth}px` }))

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function closeSidebar() {
  sidebarOpen.value = false
}
</script>

<template>
  <div class="app-layout">
    <div v-if="sidebarOpen" class="sidebar-overlay" @click="closeSidebar"></div>
    <div class="sidebar-wrap" :class="{ open: sidebarOpen }">
      <Sidebar @navigate="closeSidebar" />
    </div>
    <button class="menu-toggle" aria-label="메뉴" @click="toggleSidebar">☰</button>
    <main class="app-main">
      <div class="app-main-inner" :style="innerStyle">
        <slot />
      </div>
    </main>
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

.app-main {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
  padding: 36px 48px 72px;
}

.app-main-inner {
  width: 100%;
  min-width: 0;
}

@media (max-width: 1024px) {
  .app-main {
    padding: 28px 24px 56px;
  }
}

@media (max-width: 768px) {
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
    padding: 68px 16px 40px;
  }
}
</style>
