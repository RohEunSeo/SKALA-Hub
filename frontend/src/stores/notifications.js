// 알림벨(전체 공지) - 조회량이 적어 벨을 열 때마다 새로 불러옴(캐싱 안 함), 읽음 처리 시 로컬 상태도 같이 갱신.
// "내 알림"(개인 알림)은 백엔드는 그대로 있지만 화면 UI는 다음 라운드에서 다시 노출할 예정이라 지금은 조회하지 않음
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchAnnouncements, fetchUnreadCount, markAnnouncementsRead } from '../api/notifications'

export const useNotificationsStore = defineStore('notifications', () => {
  const announcements = ref([])
  const announcementUnread = ref(0)

  const loading = ref(false)
  const error = ref('')

  async function loadUnreadCount() {
    try {
      const { data } = await fetchUnreadCount()
      announcementUnread.value = data.announcementUnread
    } catch {
      // 뱃지 카운트 실패는 조용히 무시 - 벨을 열면 목록 조회에서 다시 시도됨
    }
  }

  async function loadAll() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await fetchAnnouncements()
      announcements.value = data
      announcementUnread.value = announcements.value.filter((a) => !a.isRead).length
    } catch {
      error.value = '알림을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  async function markAllAnnouncementsRead() {
    announcements.value = announcements.value.map((a) => ({ ...a, isRead: true }))
    announcementUnread.value = 0
    await markAnnouncementsRead()
  }

  return {
    announcements,
    announcementUnread,
    loading,
    error,
    loadUnreadCount,
    loadAll,
    markAllAnnouncementsRead,
  }
})
