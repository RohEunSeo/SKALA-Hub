// 게시글 목록 및 필터 상태 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchPosts as fetchPostsApi } from '../api/posts'
import { fetchHomeSummary } from '../api/home'

const PAGE_SIZE = 20

export const usePostsStore = defineStore('posts', () => {
  const posts = ref([])
  const category = ref(null)
  const tag = ref(null)
  const keyword = ref('')
  const author = ref('')
  const date = ref(null)
  const page = ref(0)
  const totalPages = ref(0)
  const lastSyncedAt = ref(null)
  const loading = ref(false)
  const error = ref('')

  // 사이드바/카테고리칩에 표시할 카테고리별 게시글 수 - 여러 화면에서 공유해서 쓰도록 스토어에 캐싱
  const categoryCounts = ref([])
  const totalPostCount = ref(0)
  const categoryCountsLoaded = ref(false)

  const hasMore = computed(() => page.value + 1 < totalPages.value)

  // 월별(yyyy-MM) 필터가 아직 오지 않은 미래 달을 가리키는지 - 게시글이 없는 이유를 화면에 다르게 안내하는 데 사용
  const isFutureMonth = computed(() => {
    if (!/^\d{4}-\d{2}$/.test(date.value ?? '')) return false
    const now = new Date()
    const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    return date.value > currentMonth
  })

  async function loadCategoryCounts() {
    if (categoryCountsLoaded.value) return
    await refreshCategoryCounts()
  }

  // 관리자가 카테고리를 수정한 직후처럼 캐시된 값을 무시하고 강제로 다시 불러올 때 사용
  async function refreshCategoryCounts() {
    try {
      const { data } = await fetchHomeSummary()
      categoryCounts.value = data?.categoryCounts ?? []
      totalPostCount.value = data?.totalPostCount ?? 0
      categoryCountsLoaded.value = true
    } catch {
      // 사이드바 카테고리 개수는 부가 정보라 실패해도 조용히 무시(0개로 표시)하고 화면은 그대로 진행
      categoryCounts.value = []
      totalPostCount.value = 0
    }
  }

  function categoryCount(value) {
    return categoryCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  // 카테고리(및 학습자료 하위 태그) 필터 변경 - 목록 처음부터 다시 조회
  function setCategory(newCategory, newTag = null) {
    category.value = newCategory
    tag.value = newTag
    return fetchPosts(true)
  }

  // 키워드/작성자 검색 - 목록 처음부터 다시 조회
  function setSearch({ keyword: newKeyword, author: newAuthor }) {
    keyword.value = newKeyword ?? ''
    author.value = newAuthor ?? ''
    return fetchPosts(true)
  }

  // 기간 필터(today/week/month/YYYY-MM) 변경 - 목록 처음부터 다시 조회
  function setDate(newDate) {
    date.value = newDate
    return fetchPosts(true)
  }

  // reset=true: 1페이지부터 새로 조회, false: 다음 페이지를 이어붙임("더보기")
  async function fetchPosts(reset = false) {
    loading.value = true
    error.value = ''
    try {
      const targetPage = reset ? 0 : page.value + 1
      const { data } = await fetchPostsApi({
        category: category.value || undefined,
        tag: tag.value || undefined,
        keyword: keyword.value || undefined,
        author: author.value || undefined,
        date: date.value || undefined,
        page: targetPage,
        size: PAGE_SIZE,
      })
      const content = data?.content ?? []
      posts.value = reset ? content : [...posts.value, ...content]
      page.value = data?.page ?? 0
      totalPages.value = data?.totalPages ?? 0
      lastSyncedAt.value = data?.lastSyncedAt ?? null
    } catch {
      error.value = '게시글을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
      if (reset) posts.value = []
    } finally {
      loading.value = false
    }
  }

  return {
    posts,
    category,
    tag,
    keyword,
    author,
    date,
    page,
    totalPages,
    lastSyncedAt,
    loading,
    error,
    hasMore,
    isFutureMonth,
    categoryCounts,
    totalPostCount,
    setCategory,
    setSearch,
    setDate,
    fetchPosts,
    loadCategoryCounts,
    refreshCategoryCounts,
    categoryCount,
  }
})
