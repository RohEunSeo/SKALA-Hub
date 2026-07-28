// 로그인 사용자 정보 및 인증 상태 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const TOKEN_KEY = 'skala_hub_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY))
  const user = ref(token.value ? decodeUser(token.value) : null)

  const isAuthenticated = computed(() => !!token.value)

  // 로그인 성공 시 JWT 저장 (유저 정보는 토큰 payload에서 추출)
  function setAuth(jwtToken) {
    token.value = jwtToken
    user.value = decodeUser(jwtToken)
    localStorage.setItem(TOKEN_KEY, jwtToken)
  }

  // 로그아웃 시 인증 상태 초기화
  function clearAuth() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, user, isAuthenticated, setAuth, clearAuth }
})

function decodeUser(jwtToken) {
  try {
    const payload = jwtToken.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(json)))
  } catch {
    return null
  }
}
