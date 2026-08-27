// 대시보드 "허브 단계" 카드 날씨 위젯 배경용 - 백엔드가 OpenWeather API를 프록시(키는 백엔드에만 보관)
import http from './http'

export function fetchPangyoWeather() {
  return http.get('/api/weather/pangyo')
}
