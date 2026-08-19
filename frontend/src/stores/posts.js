// 게시글 목록 및 필터 상태 관리
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchPosts as fetchPostsApi, fetchLinkGroups as fetchLinkGroupsApi } from '../api/posts'
import { fetchHomeSummary } from '../api/home'
import { fetchHiddenLinks as fetchHiddenLinksApi } from '../api/admin'

const PAGE_SIZE = 20
// 링크 모음 탭은 URL 중복 제거를 위해 매 요청마다 필터에 맞는 게시글 전체를 다시 훑어서 그룹을 새로 계산함 -
// 페이지 크기를 작게 잡으면 스크롤할 때마다 이 무거운 재계산이 반복돼서 매번 로딩이 걸림. 채널 규모(5개월,
// 340명) 대비 넉넉한 크기로 한 번에 받아와서, 사실상 첫 로딩 한 번 이후로는 스크롤이 로컬에서만 처리되게 함
const LINK_PAGE_SIZE = 1000
// 링크 모음 탭에서 하위 분류(tag)를 typeTag(관리자가 링크 단위로 확정한 값 하나)로 거르는 카테고리 -
// backend LinkService.CATEGORY_TYPE_TAGS와 동일해야 함. 그 외 카테고리(교육생 서비스 등)는 대표
// 게시글의 원본 tags 배열로 거름(하나의 게시글에 태그가 여러 개 달릴 수 있는 케이스)
const TYPE_TAG_CATEGORIES = ['학습자료', '기타']

export const usePostsStore = defineStore('posts', () => {
  const posts = ref([])
  const category = ref(null)
  const tag = ref(null)
  const keyword = ref('')
  const author = ref('')
  const date = ref(null)
  const sort = ref('latest')
  const campus = ref(null)
  const hasLink = ref(null)
  const page = ref(0)
  const totalPages = ref(0)
  const lastSyncedAt = ref(null)
  const loading = ref(false)
  const error = ref('')
  // 목록이 처음부터(reset) 다시 조회될 때마다 증가 - 화면 쪽에서 "몇 개까지 보여줄지"를 리셋하는 신호로 사용
  const resetToken = ref(0)
  // 현재 필터 조건으로 한 번이라도 성공적으로 불러왔는지 - 피드 탭을 벗어났다 돌아왔을 때 재조회를 건너뛰는 기준
  const postsLoaded = ref(false)

  // 링크 모음 탭 - URL 기준으로 그룹핑된 카드 목록(같은 URL을 올린 게시글이 여러 개면 카드 1개로 합쳐서 옴).
  // /api/posts와는 별도 엔드포인트(/api/links)라서 게시글 목록(posts)과 독립적으로 상태를 둔다.
  // allLinkGroups는 서버에서 받아온 원본(카테고리/유형 필터링 전) - 카테고리 칩을 누를 때마다 재요청하지
  // 않도록, 카테고리/유형(tag)은 아래 linkGroups computed에서 client-side로만 거른다
  const allLinkGroups = ref([])
  const linkGroups = computed(() =>
    allLinkGroups.value.filter((group) => {
      if (category.value && group.category !== category.value) return false
      if (!tag.value) return true
      // typeTag 지원 카테고리(학습자료/기타)는 typeTag(링크 단위로 확정된 값 하나, 관리자 오버라이드
      // 포함)로, 그 외 카테고리(교육생 서비스 등)의 하위 분류는 대표 게시글의 원본 tags 배열로 거른다 -
      // 서로 다른 필드라 분기 필요
      if (TYPE_TAG_CATEGORIES.includes(category.value)) return group.typeTag === tag.value
      return group.tags?.includes(tag.value) ?? false
    }),
  )
  const linkPage = ref(0)
  const linkTotalPages = ref(0)
  const linkGroupsLoading = ref(false)
  const linkGroupsLoaded = ref(false)
  const linkResetToken = ref(0)
  // 검색/기간/정렬/캠퍼스 등 서버 재조회가 필요한 필터 변경 여부(카테고리/유형은 client-side라 여기 해당 안 됨) -
  // true면 화면에 이전 카드들을 그대로 둔 채 살짝 dim 처리 + 스피너 오버레이만 보여줌
  const linkFilterLoading = ref(false)

  // 관리자 전용 "숨김" 뷰 - 링크 모음 탭 정렬 드롭다운에서 켜면 hiddenLinkGroups를 대신 보여줌
  const showHiddenLinks = ref(false)
  const hiddenLinkGroups = ref([])
  const hiddenLinkGroupsLoading = ref(false)

  // 사이드바/카테고리칩에 표시할 카테고리별 게시글 수 - 여러 화면에서 공유해서 쓰도록 스토어에 캐싱
  const categoryCounts = ref([])
  const tagCounts = ref([])
  const totalPostCount = ref(0)
  // 링크 모음 탭용 카테고리 칩 개수 - 게시글 수가 아니라 attachments 배열 원소 총합(카테고리 칩과 동일한 캐싱 주기로 관리)
  const linkCategoryCounts = ref([])
  const linkTagCounts = ref([])
  const totalLinkCount = ref(0)
  const categoryCountsLoaded = ref(false)

  const hasMore = computed(() => page.value + 1 < totalPages.value)
  const linkHasMore = computed(() => linkPage.value + 1 < linkTotalPages.value)

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
      tagCounts.value = data?.tagCounts ?? []
      totalPostCount.value = data?.totalPostCount ?? 0
      linkCategoryCounts.value = data?.linkCategoryCounts ?? []
      linkTagCounts.value = data?.linkTagCounts ?? []
      totalLinkCount.value = data?.totalLinkCount ?? 0
      categoryCountsLoaded.value = true
    } catch {
      // 사이드바 카테고리 개수는 부가 정보라 실패해도 조용히 무시(0개로 표시)하고 화면은 그대로 진행
      categoryCounts.value = []
      tagCounts.value = []
      totalPostCount.value = 0
      linkCategoryCounts.value = []
      linkTagCounts.value = []
      totalLinkCount.value = 0
    }
  }

  function categoryCount(value) {
    return categoryCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  function tagCount(value) {
    return tagCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  function linkCategoryCount(value) {
    return linkCategoryCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  function linkTagCount(value) {
    return linkTagCounts.value?.find((item) => item.category === value)?.count ?? 0
  }

  // 현재 활성 탭(게시글/링크 모음)에 맞는 목록을 처음부터 다시 조회 - 필터 변경 setter들이 공용으로 사용
  function refetchCurrent() {
    return hasLink.value === true ? fetchLinkGroups(true) : fetchPosts(true)
  }

  // 카테고리(및 학습자료 하위 태그) 필터 변경 - 목록 처음부터 다시 조회
  function setCategory(newCategory, newTag = null) {
    category.value = newCategory
    tag.value = newTag
    // 링크 모음 탭에서는 카테고리/유형이 client-side 필터라(위 linkGroups computed) 값만 바꾸면
    // 목록이 알아서 다시 계산됨 - 네트워크 요청이 필요 없음
    if (hasLink.value === true) return
    return refetchCurrent()
  }

  // 키워드/작성자 검색 - 목록 처음부터 다시 조회
  function setSearch({ keyword: newKeyword, author: newAuthor }) {
    keyword.value = newKeyword ?? ''
    author.value = newAuthor ?? ''
    return refetchCurrent()
  }

  // 기간 필터(today/week/month/YYYY-MM) 변경 - 목록 처음부터 다시 조회
  function setDate(newDate) {
    date.value = newDate
    return refetchCurrent()
  }

  // 정렬(latest/popular/oldest) 변경 - 목록 처음부터 다시 조회
  function setSort(newSort) {
    sort.value = newSort
    return refetchCurrent()
  }

  // 캠퍼스(4층/5층) 필터 변경 - 목록 처음부터 다시 조회
  function setCampus(newCampus) {
    campus.value = newCampus
    return refetchCurrent()
  }

  // 링크 모음 탭 전환(true/null) - 전환된 탭에 맞는 목록을 처음부터 다시 조회
  function setHasLink(newHasLink) {
    hasLink.value = newHasLink
    return refetchCurrent()
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
        campus: campus.value || undefined,
        hasLink: hasLink.value ?? undefined,
        sort: sort.value !== 'latest' ? sort.value : undefined,
        page: targetPage,
        size: PAGE_SIZE,
      })
      const content = data?.content ?? []
      posts.value = reset ? content : [...posts.value, ...content]
      page.value = data?.page ?? 0
      totalPages.value = data?.totalPages ?? 0
      lastSyncedAt.value = data?.lastSyncedAt ?? null
      if (reset) {
        resetToken.value += 1
        postsLoaded.value = true
      }
    } catch {
      error.value = '게시글을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
      if (reset) posts.value = []
    } finally {
      loading.value = false
    }
  }

  // 피드 화면 마운트 시 호출 - 이미 같은 조건으로 불러온 적 있으면 API 재호출 없이 캐시된 데이터를 그대로 사용.
  // 실제로 새로 불러왔으면 true, 캐시를 그대로 썼으면 false를 반환 (호출부가 화면 노출량을 채울지 판단하는 데 사용)
  async function ensureLoaded() {
    if (postsLoaded.value) return false
    await fetchPosts(true)
    return true
  }

  // 동기화로 게시글 데이터가 바뀌었을 때 호출 - 다음 피드 방문 시 다시 불러오게 함
  function invalidateCache() {
    postsLoaded.value = false
  }

  // 카테고리/유형(tag)은 서버에 안 보내고 항상 전체를 받아온 뒤 client-side로 거른다(아래 linkGroups
  // computed) - 카테고리 칩을 누를 때마다 네트워크 왕복이 생겨서 배포 환경에서 1~2초씩 걸리던 걸 없애기 위함.
  // 검색어/기간/정렬/캠퍼스는 그대로 서버 필터를 씀(변경 빈도가 낮고, client에서 완전히 재현하려면
  // 작성일·저장수 등 별도 필드가 더 필요해 복잡도가 커짐)
  // reset=true: 1페이지부터 새로 조회, false: 다음 페이지를 이어붙임(스크롤 더보기)
  async function fetchLinkGroups(reset = false) {
    linkGroupsLoading.value = true
    // 이미 카드가 떠 있는 상태에서의 필터 변경(reset)이면 목록을 비우지 않고 오버레이 스피너만 띄움 -
    // 목록이 비었다 다시 채워지는 깜빡임 없이 즉시 전환되는 것처럼 보이게 함
    if (reset && linkGroups.value.length > 0) linkFilterLoading.value = true
    error.value = ''
    try {
      const targetPage = reset ? 0 : linkPage.value + 1
      const { data } = await fetchLinkGroupsApi({
        keyword: keyword.value || undefined,
        author: author.value || undefined,
        date: date.value || undefined,
        campus: campus.value || undefined,
        sort: sort.value !== 'latest' ? sort.value : undefined,
        page: targetPage,
        size: LINK_PAGE_SIZE,
      })
      const content = data?.content ?? []
      allLinkGroups.value = reset ? content : [...allLinkGroups.value, ...content]
      linkPage.value = data?.page ?? 0
      linkTotalPages.value = data?.totalPages ?? 0
      if (reset) {
        linkResetToken.value += 1
        linkGroupsLoaded.value = true
      }
    } catch {
      error.value = '링크를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
      if (reset) allLinkGroups.value = []
    } finally {
      linkGroupsLoading.value = false
      linkFilterLoading.value = false
    }
  }

  // 링크 모음 탭 마운트 시 호출 - ensureLoaded()의 링크 버전
  async function ensureLinkGroupsLoaded() {
    if (linkGroupsLoaded.value) return false
    await fetchLinkGroups(true)
    return true
  }

  // 관리자가 링크 제목/만든사람 등을 수정한 직후 - 다음 방문 시 다시 불러오게 함
  function invalidateLinkGroupsCache() {
    linkGroupsLoaded.value = false
  }

  // 관리자 전용 "숨김" 뷰 - 필터 없이 숨긴 링크 전체를 가져옴(개수가 적어 페이지네이션 없음)
  async function fetchHiddenLinkGroups() {
    hiddenLinkGroupsLoading.value = true
    try {
      const { data } = await fetchHiddenLinksApi()
      hiddenLinkGroups.value = data ?? []
    } finally {
      hiddenLinkGroupsLoading.value = false
    }
  }

  // 정렬 드롭다운의 "숨김" 옵션 토글 - 켜면 숨긴 링크 목록을 새로 불러옴
  function setShowHiddenLinks(value) {
    showHiddenLinks.value = value
    if (value) {
      fetchHiddenLinkGroups()
    }
  }

  return {
    posts,
    category,
    tag,
    keyword,
    author,
    date,
    sort,
    campus,
    hasLink,
    page,
    totalPages,
    lastSyncedAt,
    loading,
    error,
    resetToken,
    postsLoaded,
    hasMore,
    linkGroups,
    linkPage,
    linkTotalPages,
    linkGroupsLoading,
    linkFilterLoading,
    linkGroupsLoaded,
    linkResetToken,
    linkHasMore,
    showHiddenLinks,
    hiddenLinkGroups,
    hiddenLinkGroupsLoading,
    isFutureMonth,
    categoryCounts,
    tagCounts,
    totalPostCount,
    linkCategoryCounts,
    linkTagCounts,
    totalLinkCount,
    setCategory,
    setSearch,
    setDate,
    setSort,
    setCampus,
    setHasLink,
    fetchPosts,
    ensureLoaded,
    invalidateCache,
    fetchLinkGroups,
    ensureLinkGroupsLoaded,
    invalidateLinkGroupsCache,
    fetchHiddenLinkGroups,
    setShowHiddenLinks,
    loadCategoryCounts,
    refreshCategoryCounts,
    categoryCount,
    tagCount,
    linkCategoryCount,
    linkTagCount,
  }
})
