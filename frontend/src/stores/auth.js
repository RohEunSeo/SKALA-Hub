// 로그인 사용자 정보 및 인증 상태 관리
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(null)

  // 로그인 성공 시 사용자 정보와 토큰 저장
  function setAuth(userInfo, jwtToken) {
    user.value = userInfo
    token.value = jwtToken
  }

  // 로그아웃 시 인증 상태 초기화
  function clearAuth() {
    user.value = null
    token.value = null
  }

  return { user, token, setAuth, clearAuth }
})
