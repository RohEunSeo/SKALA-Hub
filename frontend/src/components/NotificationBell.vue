<script setup>
// 홈 화면 상단 알림벨 - 전체 공지 드롭다운 (내 알림 탭은 다음 라운드에서 추가 예정, 지금은 숨김)
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationsStore } from '../stores/notifications'
import { formatRelativeTime } from '../utils/relativeTime'

const router = useRouter()
const notificationsStore = useNotificationsStore()

const open = ref(false)
const wrapRef = ref(null)

onMounted(() => {
  notificationsStore.loadUnreadCount()
  document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleOutsideClick)
})

function handleOutsideClick(event) {
  if (open.value && wrapRef.value && !wrapRef.value.contains(event.target)) {
    open.value = false
  }
}

function toggleOpen() {
  open.value = !open.value
  if (open.value) {
    notificationsStore.loadAll()
  }
}

function goToLink(path) {
  if (!path) return
  open.value = false
  router.push(path)
}
</script>

<template>
  <div ref="wrapRef" class="bell-wrap">
    <button class="bell-btn" title="알림" @click.stop="toggleOpen">
      🔔
      <span v-if="notificationsStore.announcementUnread > 0" class="bell-badge">{{
        notificationsStore.announcementUnread > 9 ? '9+' : notificationsStore.announcementUnread
      }}</span>
    </button>

    <div v-if="open" class="bell-panel" @click.stop>
      <div class="bell-panel-title">📢 전체 공지</div>

      <div class="bell-body">
        <div v-if="notificationsStore.loading" class="bell-empty">불러오는 중...</div>
        <div v-else-if="notificationsStore.announcements.length === 0" class="bell-empty">공지가 없습니다.</div>
        <div
          v-for="item in notificationsStore.announcements"
          :key="item.id"
          class="bell-item"
          :class="{ unread: !item.isRead }"
        >
          <span class="bell-item-badge">{{ item.badgeType }}</span>
          <div class="bell-item-body">
            <div class="bell-item-title">{{ item.title }}</div>
            <div class="bell-item-meta">
              <span class="bell-item-time">{{ formatRelativeTime(item.createdAt) }}</span>
              <span v-if="item.linkPath" class="bell-item-link-hint" @click.stop="goToLink(item.linkPath)"
                >{{ item.linkLabel || '바로가기' }} ›</span
              >
              <span v-if="item.linkPath2" class="bell-item-link-hint" @click.stop="goToLink(item.linkPath2)"
                >{{ item.linkLabel2 || '바로가기' }} ›</span
              >
            </div>
          </div>
        </div>
        <div
          v-if="notificationsStore.announcements.length > 0"
          class="bell-read-all"
          @click="notificationsStore.markAllAnnouncementsRead()"
        >
          모두 읽음 처리
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bell-wrap {
  position: relative;
}

.bell-btn {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: none;
  background: #f1eefc;
  font-size: 23px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease;
}

.bell-btn:hover {
  background: #e3ddf7;
}

.bell-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 999px;
  background: #e01e5a;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bell-panel {
  position: absolute;
  top: 50px;
  right: 0;
  width: 320px;
  max-height: 400px;
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid rgba(26, 26, 46, 0.08);
  box-shadow: 0 12px 32px rgba(26, 26, 46, 0.14);
  display: flex;
  flex-direction: column;
  z-index: 100;
  overflow: hidden;
}

.bell-panel-title {
  padding: 14px 16px 10px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1a1a2e;
  border-bottom: 1px solid rgba(26, 26, 46, 0.06);
}

.bell-body {
  overflow-y: auto;
  max-height: 320px;
  padding: 6px 8px;
}

.bell-empty {
  padding: 24px 8px;
  text-align: center;
  font-size: 12.5px;
  color: #636e72;
}

.bell-item {
  display: flex;
  gap: 8px;
  padding: 10px 8px;
  border-radius: 10px;
}

.bell-item.unread {
  background: #f8f7fd;
}

.bell-item-badge {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f1eefc;
  color: #4a3f8f;
  font-size: 10.5px;
  font-weight: 700;
  height: fit-content;
}

.bell-item-title {
  font-size: 12.5px;
  color: #1a1a2e;
  font-weight: 600;
  line-height: 1.4;
}

.bell-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.bell-item-time {
  font-size: 11px;
  color: #636e72;
}

.bell-item-link-hint {
  font-size: 11px;
  font-weight: 700;
  color: #4a3f8f;
  cursor: pointer;
}

.bell-item-link-hint:hover {
  text-decoration: underline;
}

.bell-read-all {
  margin-top: 4px;
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: #4a3f8f;
  cursor: pointer;
}

.bell-read-all:hover {
  text-decoration: underline;
}
</style>
