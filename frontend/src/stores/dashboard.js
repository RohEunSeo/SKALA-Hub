// 마이페이지 "대시보드" 탭 (관리자 전용) - 탭이 열려있는 동안 주기적으로 재조회해서 실시간처럼 갱신
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchDashboardSummary } from '../api/dashboard'

const POLL_INTERVAL_MS = 45000

export const useDashboardStore = defineStore('dashboard', () => {
  const summary = ref(null)
  const loading = ref(false)
  const error = ref('')

  let pollTimer = null

  async function loadSummary() {
    // 폴링 중 재조회 실패로 화면이 깜빡이지 않도록, 이미 데이터가 있으면 로딩 스피너는 최초 1회만 표시
    if (!summary.value) loading.value = true
    try {
      const { data } = await fetchDashboardSummary()
      summary.value = data
      error.value = ''
    } catch {
      if (!summary.value) error.value = '대시보드 데이터를 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  function startPolling() {
    if (pollTimer) return
    loadSummary()
    pollTimer = setInterval(loadSummary, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    clearInterval(pollTimer)
    pollTimer = null
  }

  return {
    summary,
    loading,
    error,
    loadSummary,
    startPolling,
    stopPolling,
  }
})
