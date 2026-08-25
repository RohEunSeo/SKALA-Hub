// 대시보드 "허브 단계" 카드의 날씨 위젯 배경 - 판교 실시간 날씨/기온
// 날씨는 장식 요소라 실패해도 조용히 무시(카드 자체는 정상 표시) + 자주 바뀌지 않으니 30분 TTL로 캐싱
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchPangyoWeather } from '../api/weather'

const TTL_MS = 30 * 60 * 1000
const POLL_INTERVAL_MS = 30 * 60 * 1000

// WMO 날씨 코드 -> 배경 테마 단순화
// 2(구름 조금)까지는 실제로는 화창하게 느껴지는 경우가 많아 sunny로 분류하고, 완전히 흐린 3(overcast)만 cloudy로 둔다
function themeFromWeatherCode(code) {
  if (code === 0 || code === 1 || code === 2) return 'sunny'
  if (code === 3) return 'cloudy'
  if (code === 45 || code === 48) return 'foggy'
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return 'rainy'
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snowy'
  if (code >= 95) return 'stormy'
  return 'cloudy'
}

export const useWeatherStore = defineStore('weather', () => {
  const condition = ref(null)
  const temperature = ref(null)
  const isDay = ref(true)
  const loading = ref(false)
  const lastFetchedAt = ref(0)

  let pollTimer = null

  async function loadWeather() {
    loading.value = true
    try {
      const { data } = await fetchPangyoWeather()
      const current = data?.current
      if (current) {
        condition.value = themeFromWeatherCode(current.weather_code)
        temperature.value = Math.round(current.temperature_2m)
        isDay.value = current.is_day === 1
        lastFetchedAt.value = Date.now()
      }
    } catch {
      // 날씨는 장식용이라 실패해도 화면에 에러를 보여주지 않고 조용히 무시(배경 없이 이모지만 표시됨)
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded() {
    if (Date.now() - lastFetchedAt.value < TTL_MS) return
    return loadWeather()
  }

  function startPolling() {
    if (pollTimer) return
    ensureLoaded()
    pollTimer = setInterval(loadWeather, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    clearInterval(pollTimer)
    pollTimer = null
  }

  return {
    condition,
    temperature,
    isDay,
    loading,
    ensureLoaded,
    startPolling,
    stopPolling,
  }
})
