// 대시보드 탭 요약 정보 API 호출 (로그인한 사용자면 누구나)
import http from './http'

export function fetchDashboardSummary() {
  return http.get('/api/dashboard/summary')
}
