// 슬랙 동기화 관련 API 호출
import http from './http'

// 관리자 전체 동기화 트리거
export function triggerSync() {
  return http.post('/api/slack/sync')
}
