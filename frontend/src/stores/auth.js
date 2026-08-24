// 로그인 사용자 정보 및 인증 상태 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const TOKEN_KEY = 'skala_hub_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY))
  const user = ref(token.value ? decodeUser(token.value) : null)
  // 관리자가 일반 학생 화면을 미리보기 위한 모드 전환 - 실제 권한(isAdmin)과는 별개로,
  // 새로고침/재로그인하면 항상 'admin'으로 초기화됨(세션 간 유지 안 함 - 의도적)
  const viewMode = ref('admin')

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')
  // 실제 관리자 여부와 미리보기 모드를 합쳐서, 컴포넌트들은 이 값 하나만 보고 관리자 전용 UI를 노출하면 됨
  const effectiveIsAdmin = computed(() => isAdmin.value && viewMode.value === 'admin')

  // 로그인 성공 시 JWT 저장 (유저 정보는 토큰 payload에서 추출)
  function setAuth(jwtToken) {
    token.value = jwtToken
    user.value = decodeUser(jwtToken)
    viewMode.value = 'admin'
    localStorage.setItem(TOKEN_KEY, jwtToken)
  }

  // 로그아웃 시 인증 상태 초기화
  function clearAuth() {
    token.value = null
    user.value = null
    viewMode.value = 'admin'
    localStorage.removeItem(TOKEN_KEY)
  }

  // 관리자 모드 ↔ 일반 모드 전환 (실제 관리자만 의미 있음 - 일반 유저는 토글 자체를 노출하지 않음)
  function toggleViewMode() {
    viewMode.value = viewMode.value === 'admin' ? 'user' : 'admin'
  }

  return {
    token,
    user,
    isAuthenticated,
    isAdmin,
    viewMode,
    effectiveIsAdmin,
    setAuth,
    clearAuth,
    toggleViewMode,
  }
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
