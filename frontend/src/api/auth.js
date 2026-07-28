// 로그인/인증 관련 API
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

// 슬랙 로그인 페이지로 이동시키는 URL (백엔드가 슬랙 인가 화면으로 리다이렉트)
export function getSlackLoginUrl() {
  return `${API_BASE_URL}/api/auth/slack`
}
