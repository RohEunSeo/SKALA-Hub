// 로그인/인증 관련 API
import http from './http'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

// 슬랙 로그인 페이지로 이동시키는 URL (백엔드가 슬랙 인가 화면으로 리다이렉트)
export function getSlackLoginUrl() {
  return `${API_BASE_URL}/api/auth/slack`
}

// 구글 로그인 - 팝업(Google Identity Services)에서 받은 인가 코드를 전달, 이미 연동된 계정만 성공
export function loginWithGoogleCode(code) {
  return http.post('/api/auth/google/login', { code })
}
