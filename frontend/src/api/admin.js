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

// 링크 모음 탭 카드 제목 수동 수정 / 숨김 처리 (url 기준, 전역)
export function updateLinkAsAdmin(payload) {
  return http.patch('/api/admin/links', payload)
}

// 슬랙 재수집 없이 - DB에 이미 있는 게시글 본문에서 아직 미리보기 캐시가 없는 링크만 다시 fetch 시도
export function backfillLinkPreviews() {
  return http.post('/api/admin/links/backfill')
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
