// 백엔드 API 공통 axios 인스턴스
import axios from 'axios'
import { TOKEN_KEY } from '../stores/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

// 로그인 토큰이 있으면 모든 요청에 자동으로 첨부 (북마크 등 인증 필요한 API용)
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// VITE_API_BASE_URL이 잘못 설정되면 API 요청이 프론트 자신의 index.html로 리라이트되어
// HTML 문자열을 200 OK로 받는 경우가 있음 - JSON을 기대하는데 문자열이 오면 바로 에러로 처리
http.interceptors.response.use((response) => {
  if (typeof response.data === 'string' && response.data.trim().startsWith('<')) {
    return Promise.reject(
      new Error('API 응답이 JSON이 아닙니다 (VITE_API_BASE_URL 설정을 확인하세요)'),
    )
  }
  return response
})

export default http
