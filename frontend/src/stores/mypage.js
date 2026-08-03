// 마이페이지 통계/내가 올린 글·저장한 글·반응한 글 - 탭 전환으로 컴포넌트가 재마운트돼도
// 재조회하지 않도록 스토어에 캐싱
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchMyStats, fetchMyPosts } from '../api/mypage'

const PAGE_SIZE = 5

export const useMyPageStore = defineStore('mypage', () => {
  const stats = ref(null)
  const statsLoading = ref(false)
  const statsError = ref('')

  const activeTab = ref('posts')
  const posts = ref([])
  const page = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const postsError = ref('')

  // 한 번이라도 성공적으로 불러왔는지 - 탭을 벗어났다 돌아왔을 때 재조회를 건너뛰는 기준
  const loaded = ref(false)

  async function loadStats() {
    statsLoading.value = true
    statsError.value = ''
    try {
      const { data } = await fetchMyStats()
      stats.value = data
    } catch {
      statsError.value = '통계를 불러오지 못했습니다.'
    } finally {
      statsLoading.value = false
    }
  }

  async function loadPosts() {
    loading.value = true
    postsError.value = ''
    try {
      const { data } = await fetchMyPosts(activeTab.value, page.value, PAGE_SIZE)
      posts.value = data?.content ?? []
      totalPages.value = data?.totalPages ?? 0
    } catch {
      postsError.value = '게시글을 불러오지 못했습니다.'
      posts.value = []
    } finally {
      loading.value = false
    }
  }

  // 마이페이지 마운트 시 호출 - 이미 불러온 적 있으면 API 재호출 없이 캐시된 데이터를 그대로 사용
  async function ensureLoaded() {
    if (loaded.value) return
    await Promise.all([loadStats(), loadPosts()])
    loaded.value = true
  }

  function setTab(tab) {
    if (activeTab.value === tab) return
    activeTab.value = tab
    page.value = 0
    return loadPosts()
  }

  function prevPage() {
    if (page.value > 0) {
      page.value -= 1
      return loadPosts()
    }
  }

  function nextPage() {
    if (page.value + 1 < totalPages.value) {
      page.value += 1
      return loadPosts()
    }
  }

  // 동기화로 게시글/댓글/반응 데이터가 바뀌었을 때 호출 - 다음 마이페이지 방문 시 다시 불러오게 함
  function invalidateCache() {
    loaded.value = false
  }

  return {
    stats,
    statsLoading,
    statsError,
    activeTab,
    posts,
    page,
    totalPages,
    loading,
    postsError,
    loaded,
    loadStats,
    loadPosts,
    ensureLoaded,
    setTab,
    prevPage,
    nextPage,
    invalidateCache,
  }
})
