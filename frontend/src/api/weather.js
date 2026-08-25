// 대시보드 "허브 단계" 카드 날씨 위젯 배경용 - Open-Meteo(무료, API 키 불필요) 직접 호출
// http.js(백엔드 전용 axios 인스턴스, JWT 자동첨부)와는 무관하게 순수 axios로 호출한다
import axios from 'axios'

// 판교(성남시 분당구 판교로 255번길 38 인근, 판교역/판교테크노밸리 일대) 좌표 고정
// - users.campus 컬럼이 사실상 전원 "판교"라 캠퍼스 분기 불필요
const PANGYO_LAT = 37.4008
const PANGYO_LON = 127.1119

export function fetchPangyoWeather() {
  return axios.get('https://api.open-meteo.com/v1/forecast', {
    params: {
      latitude: PANGYO_LAT,
      longitude: PANGYO_LON,
      current: 'temperature_2m,weather_code,is_day',
      timezone: 'Asia/Seoul',
    },
  })
}
