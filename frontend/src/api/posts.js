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
