// 저장하기(북마크) 관련 API 호출
import http from './http'

// 로그인한 유저가 저장한 게시글 id 목록
export function fetchBookmarks() {
  return http.get('/api/bookmarks')
}

// 게시글 저장하기
export function saveBookmark(postId) {
  return http.post('/api/bookmarks', { postId })
}

// 게시글 저장 취소
export function removeBookmark(postId) {
  return http.delete(`/api/bookmarks/${postId}`)
}
