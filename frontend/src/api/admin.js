// 관리자 전용 API 호출 (role=admin 로그인 필요 - 백엔드에서 강제)
import http from './http'

// 슬랙 채널 최근 N일 동기화 (가벼움 - API 호출량 적음)
export function triggerSync() {
  return http.post('/api/admin/sync')
}

// 슬랙 채널 히스토리 전체 재수집 (무거움 - 평소엔 새벽에 자동 실행됨)
export function triggerFullSync() {
  return http.post('/api/admin/sync-full')
}

// 미분류(카테고리 없음) 게시글 목록
export function fetchUncategorizedPosts(page, size) {
  return http.get('/api/admin/posts/uncategorized', { params: { page, size } })
}

// 게시글 카테고리/태그/핀 고정 수동 수정
export function updatePostAsAdmin(id, payload) {
  return http.patch(`/api/admin/posts/${id}`, payload)
}

// 순위보드에서 제외된 게시글 목록 (홈 화면 관리자 전용 패널)
export function fetchExcludedFromRanking() {
  return http.get('/api/admin/posts/excluded-from-ranking')
}

// 링크 모음 탭 카드 제목 수동 수정 / 숨김 처리 (url 기준, 전역)
export function updateLinkAsAdmin(payload) {
  return http.patch('/api/admin/links', payload)
}

// 슬랙 재수집 없이 - DB에 이미 있는 게시글 본문에서 아직 미리보기 캐시가 없는 링크만 다시 fetch 시도
export function backfillLinkPreviews() {
  return http.post('/api/admin/links/backfill')
}

// 숨긴 링크 목록 (복원 전까지 링크 모음 어디에도 안 보이므로 관리자 전용 화면에서만 확인 가능)
export function fetchHiddenLinks() {
  return http.get('/api/admin/links/hidden')
}

// 미분류 게시글 전체 일괄 재분류
export function classifyAllUncategorized() {
  return http.post('/api/admin/posts/classify-all')
}

// 동기화 실패 목록 (슬랙에는 알리지 않고 관리자 모드에서만 확인)
export function fetchSyncFailures() {
  return http.get('/api/admin/sync-failures')
}

// 슬랙 봇이 남긴 동기화 안내 댓글 목록
export function fetchBotReplies() {
  return http.get('/api/admin/bot-replies')
}

// 봇 댓글 삭제 (ts로 식별)
export function deleteBotReply(ts) {
  return http.delete('/api/admin/bot-replies', { params: { ts } })
}

// 봇 댓글 내용 수정
export function updateBotReply(ts, content) {
  return http.put('/api/admin/bot-replies', { content }, { params: { ts } })
}

// 로컬 환경 동기화로 보류된 성공 알림을 배포 환경에서 지금 전송
export function sendPendingNotification(postId) {
  return http.post(`/api/admin/pending-notifications/${postId}/send`)
}

// 전체 공지 목록 (관리자 모드 - 읽음 여부 무관)
export function fetchAdminAnnouncements() {
  return http.get('/api/admin/announcements')
}

// 전체 공지 작성 - 저장 즉시 모든 유저의 "전체 공지" 탭에 노출
export function createAnnouncement(payload) {
  return http.post('/api/admin/announcements', payload)
}

// 전체 공지 수정 - 이미 읽은 유저의 읽음 상태는 그대로 유지됨(다시 안읽음으로 뜨지 않음)
export function updateAnnouncement(id, payload) {
  return http.patch(`/api/admin/announcements/${id}`, payload)
}

// 전체 공지 삭제
export function deleteAnnouncement(id) {
  return http.delete(`/api/admin/announcements/${id}`)
}
