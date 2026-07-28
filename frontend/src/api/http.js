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

export default http
