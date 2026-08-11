// 마이페이지 통계/내가 올린 글·저장한 글·반응한 글 - 탭 전환으로 컴포넌트가 재마운트돼도
// 재조회하지 않도록 스토어에 캐싱
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchMyStats, fetchMyPosts, fetchMyCategoryCounts } from '../api/mypage'

const PAGE_SIZE = 4

export const useMyPageStore = defineStore('mypage', () => {
  const stats = ref(null)
  const statsLoading = ref(false)
  const statsError = ref('')

  const activeTab = ref('posts')
  const category = ref(null)
  const tag = ref(null)
  const posts = ref([])
  const page = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const postsError = ref('')

  // 카테고리 필터 탭 옆에 표시할 탭별 카테고리/태그 개수
  const categoryCounts = ref([])
  const tagCounts = ref([])

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
      const { data } = await fetchMyPosts(
        activeTab.value,
        category.value || undefined,
        tag.value || undefined,
        page.value,
        PAGE_SIZE,
      )
      posts.value = data?.content ?? []
      totalPages.value = data?.totalPages ?? 0
    } catch {
      postsError.value = '게시글을 불러오지 못했습니다.'
      posts.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadCategoryCounts() {
    try {
      const { data } = await fetchMyCategoryCounts(activeTab.value)
      categoryCounts.value = data?.categoryCounts ?? []
      tagCounts.value = data?.tagCounts ?? []
    } catch {
      // 카테고리 옆 개수는 부가 정보라 실패해도 조용히 무시(0개로 표시)하고 화면은 그대로 진행
      categoryCounts.value = []
      tagCounts.value = []
    }
  }

  function categoryCount(value) {
    return categoryCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  function tagCount(value) {
    return tagCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  // 마이페이지 마운트 시 호출 - 이미 불러온 적 있으면 API 재호출 없이 캐시된 데이터를 그대로 사용
  async function ensureLoaded() {
    if (loaded.value) return
    await Promise.all([loadStats(), loadPosts(), loadCategoryCounts()])
    loaded.value = true
  }

  function setTab(tab) {
    if (activeTab.value === tab) return
    activeTab.value = tab
    // 탭을 바꾸면 카테고리는 항상 "전체"로 초기화 (탭마다 카테고리 분포가 달라 유지하면 혼란스러움)
    category.value = null
    tag.value = null
    page.value = 0
    return Promise.all([loadPosts(), loadCategoryCounts()])
  }

  function setCategory(newCategory, newTag = null) {
    if (category.value === newCategory && tag.value === newTag) return
    category.value = newCategory
    tag.value = newTag
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
    category,
    tag,
    posts,
    page,
    totalPages,
    loading,
    postsError,
    categoryCounts,
    tagCounts,
    loaded,
    loadStats,
    loadPosts,
    loadCategoryCounts,
    categoryCount,
    tagCount,
    ensureLoaded,
    setTab,
    setCategory,
    prevPage,
    nextPage,
    invalidateCache,
  }
})
