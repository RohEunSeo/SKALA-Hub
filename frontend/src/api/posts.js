// 게시글 조회/수정 관련 API 호출
import http from './http'

// 게시글 목록 조회 (필터: category, tag, keyword, author, date, page, size)
export function fetchPosts(params) {
  return http.get('/api/posts', { params })
}

// 게시글 상세 조회
export function fetchPost(id) {
  return http.get(`/api/posts/${id}`)
}

// 게시글 댓글(스레드) 조회
export function fetchReplies(id) {
  return http.get(`/api/posts/${id}/replies`)
}

// AI 제목 수정 (작성자 본인 또는 관리자만 - 백엔드에서 403 처리)
export function updatePostAiTitle(id, aiTitle) {
  return http.patch(`/api/posts/${id}/ai-title`, { aiTitle })
}

// 링크 모음 탭 - URL 기준으로 그룹핑된 링크 목록 (같은 링크를 올린 게시글이 여러 개면 카드 1개로 합쳐서 내려옴)
export function fetchLinkGroups(params) {
  return http.get('/api/links', { params })
}
