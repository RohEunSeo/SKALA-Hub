// 대시보드 "허브 단계" 카드의 날씨 위젯 배경 - 판교 실시간 날씨/기온 (백엔드가 OpenWeather API 프록시)
// 날씨는 장식 요소라 실패해도 조용히 무시(카드 자체는 정상 표시) + 자주 바뀌지 않으니 30분 TTL로 캐싱
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchPangyoWeather } from '../api/weather'

const TTL_MS = 30 * 60 * 1000
const POLL_INTERVAL_MS = 30 * 60 * 1000

export const useWeatherStore = defineStore('weather', () => {
  const condition = ref(null)
  const temperature = ref(null)
  const isDay = ref(true)
  const humidity = ref(null)
  const loading = ref(false)
  const lastFetchedAt = ref(0)

  let pollTimer = null

  async function loadWeather() {
    loading.value = true
    try {
      const { data } = await fetchPangyoWeather()
      if (data) {
        condition.value = data.condition
        temperature.value = data.temperature
        isDay.value = data.isDay
        humidity.value = data.humidity
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
    humidity,
    loading,
    ensureLoaded,
    startPolling,
    stopPolling,
  }
})
