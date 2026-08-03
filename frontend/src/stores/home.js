// 홈 화면 요약/순위보드 - 탭 전환으로 컴포넌트가 재마운트돼도 재조회하지 않도록 스토어에 캐싱
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchHomeSummary, fetchHomeLeaderboard } from '../api/home'

export const useHomeStore = defineStore('home', () => {
  const summary = ref(null)
  const summaryError = ref('')

  const leaderboard = ref(null)
  const leaderboardLoading = ref(true)
  const leaderboardError = ref('')
  const leaderboardPeriod = ref('all')

  // 한 번이라도 성공적으로 불러왔는지 - 탭을 벗어났다 돌아왔을 때 재조회를 건너뛰는 기준
  const loaded = ref(false)

  async function loadSummary() {
    summaryError.value = ''
    try {
      const { data } = await fetchHomeSummary()
      summary.value = data
    } catch {
      summaryError.value = '요약 정보를 불러오지 못했습니다.'
    }
  }

  async function loadLeaderboard() {
    leaderboardLoading.value = true
    leaderboardError.value = ''
    try {
      const { data } = await fetchHomeLeaderboard(leaderboardPeriod.value)
      leaderboard.value = data
    } catch {
      leaderboardError.value = '순위보드를 불러오지 못했습니다.'
    } finally {
      leaderboardLoading.value = false
    }
  }

  // 홈 화면 마운트 시 호출 - 이미 불러온 적 있으면 API 재호출 없이 캐시된 데이터를 그대로 사용
  async function ensureLoaded() {
    if (loaded.value) return
    await Promise.all([loadSummary(), loadLeaderboard()])
    loaded.value = true
  }

  // 전체/이번주 탭 전환은 캐싱 대상이 아니라 매번 새로 조회 (카테고리 필터와 동일한 사용자 액션)
  function selectPeriod(period) {
    if (leaderboardPeriod.value === period) return
    leaderboardPeriod.value = period
    return loadLeaderboard()
  }

  // 동기화로 게시글/댓글/반응 데이터가 바뀌었을 때 호출 - 다음 홈 방문 시 다시 불러오게 함
  function invalidateCache() {
    loaded.value = false
  }

  return {
    summary,
    summaryError,
    leaderboard,
    leaderboardLoading,
    leaderboardError,
    leaderboardPeriod,
    loaded,
    loadSummary,
    loadLeaderboard,
    ensureLoaded,
    selectPeriod,
    invalidateCache,
  }
})
