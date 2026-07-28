// 관리자 전용 API 호출 (role=admin 로그인 필요 - 백엔드에서 강제)
import http from './http'

// 슬랙 채널 전체 동기화
export function triggerSync() {
  return http.post('/api/admin/sync')
}

// 미분류(카테고리 없음) 게시글 목록
export function fetchUncategorizedPosts(page, size) {
  return http.get('/api/admin/posts/uncategorized', { params: { page, size } })
}

// 게시글 카테고리/태그/핀 고정 수동 수정
export function updatePostAsAdmin(id, payload) {
  return http.patch(`/api/admin/posts/${id}`, payload)
}

// 미분류 게시글 전체 일괄 재분류
export function classifyAllUncategorized() {
  return http.post('/api/admin/posts/classify-all')
}
