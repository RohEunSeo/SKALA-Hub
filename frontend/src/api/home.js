// 홈 화면 요약 정보 API 호출
import http from './http'

export function fetchHomeSummary() {
  return http.get('/api/home/summary')
}

export function fetchHomeLeaderboard() {
  return http.get('/api/home/leaderboard')
}
