// 마이페이지 통계/내가 올린 글/저장한 글 API 호출
import http from './http'

export function fetchMyStats() {
  return http.get('/api/mypage/stats')
}

export function fetchMyPosts(tab, page, size) {
  return http.get('/api/mypage/posts', { params: { tab, page, size } })
}
