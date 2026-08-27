// 대시보드 "허브 단계" 카드 날씨 위젯 배경용 - Open-Meteo(무료, API 키 불필요) 직접 호출
// http.js(백엔드 전용 axios 인스턴스, JWT 자동첨부)와는 무관하게 순수 axios로 호출한다
import axios from 'axios'

// SK AX 판교캠퍼스 B동(경기도 성남시 분당구 판교로255번길 38) 좌표 고정
// - users.campus 컬럼이 사실상 전원 "판교"라 캠퍼스 분기 불필요
const PANGYO_LAT = 37.4058792
const PANGYO_LON = 127.0998773

export function fetchPangyoWeather() {
  return axios.get('https://api.open-meteo.com/v1/forecast', {
    params: {
      latitude: PANGYO_LAT,
      longitude: PANGYO_LON,
      current: 'temperature_2m,weather_code,is_day,relative_humidity_2m',
      // models=kma_seamless(기상청 모델 명시 지정)를 시도해봤으나 무료 티어에서 전부 null이 내려와
      // 사용 불가 - 기본값(best_match, 지역별 최적 모델 자동 선택)이 실제로 값을 정상 반환한다
      timezone: 'Asia/Seoul',
    },
  })
}
