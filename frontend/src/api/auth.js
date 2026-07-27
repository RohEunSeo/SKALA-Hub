// 로그인/인증 관련 API 호출
import http from './http'

// 슬랙 OAuth 코드로 로그인 처리
export function login(code) {
  return http.post('/api/auth/login', { code })
}
