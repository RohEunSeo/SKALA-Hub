// 대시보드 탭 요약 정보 API 호출 (관리자 전용)
import http from './http'

export function fetchDashboardSummary() {
  return http.get('/api/admin/dashboard/summary')
}
