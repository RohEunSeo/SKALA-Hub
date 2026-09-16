// 마이페이지 통계/내가 올린 글/저장한 글 API 호출
import http from './http'

export function fetchMyStats() {
  return http.get('/api/mypage/stats')
}

export function fetchMyPosts(tab, category, tag, page, size) {
  return http.get('/api/mypage/posts', { params: { tab, category, tag, page, size } })
}

export function fetchMyCategoryCounts(tab) {
  return http.get('/api/mypage/category-counts', { params: { tab } })
}

// 계정 설정 - 구글 계정 연동 상태
export function fetchAccountLinkStatus() {
  return http.get('/api/mypage/account')
}

// 구글 계정 연동 (팝업에서 받은 인가 코드 전달)
export function linkGoogleAccount(code) {
  return http.post('/api/mypage/account/google-link', { code })
}

// 구글 계정 연동 해제 (본인)
export function unlinkGoogleAccount() {
  return http.delete('/api/mypage/account/google-link')
}
