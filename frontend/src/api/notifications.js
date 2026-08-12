// 알림(전체 공지/개인 알림) API 호출
import http from './http'

export function fetchAnnouncements() {
  return http.get('/api/announcements')
}

export function markAnnouncementsRead() {
  return http.post('/api/announcements/read-all')
}

export function fetchNotifications() {
  return http.get('/api/notifications')
}

export function markNotificationsRead() {
  return http.post('/api/notifications/read-all')
}

export function fetchUnreadCount() {
  return http.get('/api/notifications/unread-count')
}
