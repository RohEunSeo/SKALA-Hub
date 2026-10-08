<script setup>
// 게시글 피드(목록) 화면
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import SearchBar from '../components/SearchBar.vue'
import CategoryFilter from '../components/CategoryFilter.vue'
import SortFilter from '../components/SortFilter.vue'
import CampusFilter from '../components/CampusFilter.vue'
import DateFilter from '../components/DateFilter.vue'
import PostCard from '../components/PostCard.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import LinkGalleryCard from '../components/LinkGalleryCard.vue'
import LinkCardSkeleton from '../components/LinkCardSkeleton.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import CurriculumBoard from '../components/CurriculumBoard.vue'
import { usePostsStore } from '../stores/posts'
import { useBookmarksStore } from '../stores/bookmarks'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { useChatStore } from '../stores/chat'
import { useFoldersStore } from '../stores/folders'
import AiFolderBar from '../components/AiFolderBar.vue'
import { formatRelativeTime } from '../utils/relativeTime'
import { CATEGORIES } from '../constants/categories'
import { fetchCurriculumStatus } from '../api/admin'
import skLogo from '../assets/sk_logo.png'

// 층(4층/5층) 필터가 의미 있는 카테고리 - 자격증·취업/교수님/기타/교육생 서비스는 층 구분이 무의미해서 제외
const CAMPUS_CATEGORIES = ['개발 툴·환경', '학습자료']
// 기간 필터가 의미 있는 카테고리 - 자격증·취업/교수님/기타/교육생 서비스는 게시글 수가 적어 기간 필터 실익이 낮음
const DATE_CATEGORIES = ['개발 툴·환경', '학습자료']
// "🗂️ 분류" 하위 태그 필터 버튼을 보여줄 카테고리 - 학습자료는 사이드바 필터만 쓰고 있어 제외
const SUBCATEGORY_FILTER_CATEGORIES = ['교육생 서비스', '기타']
// 링크 모음 탭에서만 숨길 하위 태그 - 분실물은 링크가 달릴 일이 거의 없어 필터로서 의미가 없음(게시글
// 탭/사이드바/관리자 화면의 분류 체계는 그대로 유지, 이 화면의 링크 탭 필터 목록에서만 제외)
const LINK_TAB_HIDDEN_TAGS = { 기타: ['분실물'] }

const route = useRoute()
const postsStore = usePostsStore()
const bookmarksStore = useBookmarksStore()
const authStore = useAuthStore()
const uiStore = useUiStore()
const chatStore = useChatStore()
const foldersStore = useFoldersStore()

// 상단 탭("게시글"/"🔗 링크 모음") - 뷰 로컬 상태, 카테고리/층/기간 필터는 스토어에서 그대로 공유됨.
// 링크 모음 카드의 "게시글 보러가기"로 상세 페이지에 갔다가 뒤로가기로 돌아올 때 ?tab=links가 붙어 오면 링크 탭으로 복원
const activeTab = ref(
  route.query.tab === 'links'
    ? 'links'
    : route.query.tab === 'curriculum'
      ? 'curriculum'
      : 'posts',
)

// 공지 등에서 카테고리/태그까지 지정한 딥링크로 들어올 수 있게 함
// 예: /feed?tab=links&category=기타&tag=맛집 → 링크 모음 탭의 기타>맛집 필터가 선택된 채로 진입
if (route.query.category) {
  postsStore.category = route.query.category
  postsStore.tag = route.query.tag || null
}

// 사이드바 카테고리 클릭처럼 이 화면을 벗어나지 않고 store.hasLink가 바뀌는 경우, 탭 UI도 함께 전환
// (커리큘럼 탭에 있을 때는 hasLink를 건드리지 않으므로 이 watch가 저절로 안 불리지만, 링크 탭으로 강제
// 전환되는 경우(val이 true)는 커리큘럼 탭에서도 예외 없이 따라가야 함)
watch(
  () => postsStore.hasLink,
  (val) => {
    if (activeTab.value === 'curriculum' && !val) return
    activeTab.value = val ? 'links' : 'posts'
  },
)

function selectTab(tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  // 링크 탭엔 층 필터 UI가 없으므로, 게시글 탭에서 걸어뒀던 층 값이 안 보이는 채로 계속 적용되지 않게 초기화
  if (tab === 'links' && postsStore.campus) {
    postsStore.campus = null
  }
  // 관리자 "숨김" 뷰는 링크 탭 전용이라 다른 탭으로 나가면 꺼줌
  if (tab !== 'links' && postsStore.showHiddenLinks) {
    postsStore.setShowHiddenLinks(false)
  }
  // 커리큘럼 탭은 게시글/링크 탭과 완전히 독립된 화면이라 postsStore.hasLink(게시글↔링크 전환용)를 건드리지 않음
  if (tab === 'posts' || tab === 'links') {
    postsStore.setHasLink(tab === 'links' ? true : null)
  }
}

// 사이드바 "링크 모음 / SKALA 커리큘럼" 하위 메뉴와 탭 상태 공유 - 탭이 바뀌면 스토어에 반영하고,
// 사이드바가 (이미 피드 화면인 상태에서) 스토어 값을 바꾸면 그 탭으로 전환. selectTab이 같은 탭이면 바로
// 반환하므로 서로의 갱신이 무한히 되돌아오지 않음
watch(activeTab, (tab) => (uiStore.feedTab = tab), { immediate: true })
watch(
  () => uiStore.feedTab,
  (tab) => selectTab(tab),
)

// 이미 /feed에 있는 상태에서 알림 등을 통해 ?tab=curriculum(또는 links) 딥링크로 다시 들어오는 경우 -
// 같은 라우트라 컴포넌트가 새로 mount되지 않아 위 activeTab 초기값 로직이 다시 실행되지 않으므로,
// route.query.tab 자체를 감시해서 탭을 맞춰준다
watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'curriculum' || tab === 'links') {
      selectTab(tab)
    }
  },
)

// 챗봇이 게시글을 추천하면 보고 있던 피드에서 그 글들이 맨 위로 올라온다.
// 별도 탭으로 보내면 피드를 떠나야 해서 맥락이 끊기므로 같은 목록 안에서 처리한다.
watch(
  () => chatStore.pickSeq,
  async () => {
    if (!postsStore.pickedIds.length) return
    selectTab('posts')
    // 추천 글이 노출 범위 안에 들어오도록 보장한 뒤 목록 맨 위로
    if (visibleCount.value < postsStore.pickedIds.length) revealMore()
    await nextTick()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  },
)

// 폴더를 고르면 그 폴더에 담긴 글만 보여준다. 담긴 글이 현재 페이지 밖에 있을 수 있어서
// 스토어가 id로 마저 받아온 뒤 필터를 적용한다.
watch(
  () => [foldersStore.selected, foldersStore.map],
  () => {
    const id = foldersStore.selected
    postsStore.setFolderFilter(id ? foldersStore.idsOf(id) : null)
  },
  { deep: true, immediate: true },
)

// 폴더와 카테고리는 서로 배타적으로 둔다.
// 둘이 동시에 걸리면 "전체"인데 몇 개만 보이는 상태가 되어 뭘 보고 있는지 알 수 없다.
// (폴더 안에서 다시 카테고리로 거르는 쓰임새는 드물고, 헷갈리는 비용이 더 크다)
watch(
  () => postsStore.category,
  () => { foldersStore.selected = null },   // 카테고리를 고르면 폴더 선택 해제
)


// 피드 스캔 모션 - "게시글 검색 중" 단계에서 시작해 추천 글이 실제로 도착할 때까지 돈다.
// status.tool만 보면 '답변 정리하는 중'으로 넘어가는 순간 꺼져서, 정작 글이 올라오기 직전에
// 모션이 사라지는 어색한 구간이 생긴다.
const isScanning = ref(false)
let scanTimer = null

watch(
  () => chatStore.status?.tool,
  (tool, prev) => {
    if (tool === 'search_posts') {
      clearTimeout(scanTimer)
      isScanning.value = true
      return
    }
    // 검색 단계를 지나 대기 상태(null)가 됐는데도 결과가 안 오면(결과 없음·거절 등)
    // 무한히 돌지 않도록 잠깐 기다렸다 멈춘다
    if (isScanning.value && tool == null && prev != null) {
      clearTimeout(scanTimer)
      scanTimer = setTimeout(() => (isScanning.value = false), 2500)
    }
  },
)

// 추천 글이 도착하면 그 순간 멈춘다 - 모션이 끝나는 지점과 글이 올라오는 지점이 맞물린다
watch(
  () => chatStore.pickSeq,
  () => {
    clearTimeout(scanTimer)
    isScanning.value = false
  },
)

onUnmounted(() => clearTimeout(scanTimer))

// 추천 해제는 목록 순서가 통째로 바뀌는 동작이라 확인을 받는다
const clearAsk = ref(false)

function confirmClear() {
  postsStore.clearPicks()
  clearAsk.value = false
}

// 새 추천이 오면 폴더 필터는 풀어준다 - 추천 글이 그 폴더에 없으면 보이지 않기 때문
watch(() => chatStore.pickSeq, () => (foldersStore.selected = null))

// 링크 탭에서 관리자가 "숨김" 정렬 옵션을 켠 상태 - 이때는 카테고리/유형/기간 필터 대신 숨긴 링크 갤러리만 보여줌
const showHidden = computed(() => activeTab.value === 'links' && postsStore.showHiddenLinks)

const activeCategoryTags = computed(() => {
  const tags = CATEGORIES.find((cat) => cat.value === postsStore.category)?.tags ?? []
  if (!postsStore.hasLink) return tags
  const hidden = LINK_TAB_HIDDEN_TAGS[postsStore.category]
  return hidden ? tags.filter((t) => !hidden.includes(t.value)) : tags
})
// 학습자료는 게시글 탭에선 사이드바 필터만 쓰지만(기존 동작 유지), 링크 탭에서는 층 필터 대신
// 유형(영상/블로그·글/깃허브) 필터로 이 자리에 노출한다
const hasSubcategoryFilter = computed(
  () =>
    (SUBCATEGORY_FILTER_CATEGORIES.includes(postsStore.category) ||
      (postsStore.hasLink && postsStore.category === '학습자료')) &&
    activeCategoryTags.value.length > 0,
)
const subcategoryFilterLabel = computed(() => (postsStore.category === '학습자료' ? '🗂️ 유형 : ' : '🗂️ 분류 : '))
// 링크 모음 탭에서는 층 구분이 의미 없어 카테고리와 무관하게 숨김
const showCampusFilter = computed(
  () => !postsStore.hasLink && (!postsStore.category || CAMPUS_CATEGORIES.includes(postsStore.category)),
)
const showDateFilter = computed(() => !postsStore.category || DATE_CATEGORIES.includes(postsStore.category))

// 게시글 개수 대신 "체감 스크롤 길이"로 더보기를 끊기 위한 상태 - 텍스트가 짧은 글 20개와
// 이미지 여러 장 붙은 긴 글 20개는 실제 스크롤 길이가 몇 배씩 차이 나서, 개수 기준으로는 매번 더보기까지의
// 스크롤량이 들쑥날쑥해짐. 게시글마다 대략적인 세로 길이를 추정해 누적하고, 예산을 넘으면 그 지점에서 끊는다.
const visibleCount = ref(0)
// 평균 길이(약 600자) 글은 가중치 20 안팎, 이미지가 붙은 긴 글은 90을 넘는다.
// 예산을 90으로 두면 긴 글 하나에 다 소진돼 두세 개만 보고 "더보기"에 막힌다.
// 평범한 글 기준 10개 안팎이 이어지도록 잡은 값.
const WEIGHT_BUDGET = 260

function estimateWeight(post) {
  const textWeight = (post.content?.length ?? 0) / 40
  const imageCount = post.files?.filter((file) => file.isImage).length ?? 0
  const otherFileCount = (post.files?.length ?? 0) - imageCount
  const attachmentCount = post.attachments?.length ?? 0
  return 6 + textWeight + imageCount * 12 + otherFileCount * 3 + attachmentCount * 8
}

// 이미 불러온 게시글 중 예산이 남아있는 만큼만 추가로 노출 (API 재조회 없이 로컬에서 처리).
// 추천된 글이 맨 앞에 와 있으므로 displayPosts 기준으로 센다 - posts 기준으로 세면
// 추천 글이 노출 범위 밖으로 밀려 "위로 올라왔는데 안 보이는" 상태가 된다.
function revealMore() {
  let budget = WEIGHT_BUDGET
  while (visibleCount.value < postsStore.displayPosts.length) {
    const next = postsStore.displayPosts[visibleCount.value]
    visibleCount.value += 1
    budget -= estimateWeight(next)
    if (budget <= 0) break
  }
}

// 지금 보고 있는 범위를 한 줄로 만든다.
// 칩을 안 보이게 두면 "어떤 필터가 걸렸는지"를 세 줄을 훑어야 알 수 있다.
const DATE_LABELS = { today: '오늘', week: '이번 주', month: '이번 달' }

// 칩 앞에 붙는 대표 아이콘. 카테고리마다 정해둔 것을 그대로 쓴다
// (사이드바·카테고리 칩과 같은 기호라 눈에 익다). 전체일 땐 폴더.
// 챗봇이 실제로 뒤지는 범위는 카테고리뿐이다 (ChatPanel 의 context 와 같은 규칙).
// 예전엔 "게시글 20개 살펴보는 중"이었는데, 그 20은 화면에 로드된 수일 뿐
// 검색 범위(글 272개)와 아무 상관이 없어 틀린 정보였다.
const searchScope = computed(() => {
  const cat = CATEGORIES.find((c) => c.value === postsStore.category)
  return cat ? cat.label : '전체 피드'
})

const scopeIcon = computed(() => {
  const cat = CATEGORIES.find((c) => c.value === postsStore.category)
  return cat?.icon ?? '📁'
})

const scopeLabel = computed(() => {
  const parts = []

  const cat = CATEGORIES.find((c) => c.value === postsStore.category)
  parts.push(cat ? cat.shortLabel ?? cat.label : '전체 카테고리')

  // 하위 태그도 층·기간과 같은 규칙: 줄이 화면에 있으면 "전체"도 적는다.
  // 하나만 빼면 "교육생 서비스 61개"처럼 뭘 고른 상태인지 알 수 없다.
  if (hasSubcategoryFilter.value) {
    const sub = activeCategoryTags.value.find((t) => t.value === postsStore.tag)
    parts.push(sub ? sub.label : '전체 유형')
  }

  // 층·기간은 카테고리에 따라 화면에 아예 없을 수 있다.
  // 없는 필터를 칩에 쓰면 "4층을 고를 수도 없는데 전체 층이라고 적혀 있는" 상태가 된다.
  if (showCampusFilter.value) parts.push(postsStore.campus ?? '전체 층')
  if (showDateFilter.value) {
    const d = postsStore.date
    // 월별은 '2026-09' 형태로 들어온다
    parts.push(d ? (DATE_LABELS[d] ?? `${Number(d.slice(5))}월`) : '전체 기간')
  }

  return parts.join(' · ')
})

// 지금 보고 있는 개수. 탭마다 세는 대상이 다르다.
// - 게시글: 서버가 필터 기준으로 센 값(totalElements). 20개씩 나눠 받으므로 화면 개수로는 알 수 없다
// - 링크  : 서버는 전체를 주고 카테고리/유형은 화면에서 거른다(linkGroups) → 화면 개수가 곧 정답
// - 커리큘럼: 단계별 목록을 통째로 받는다
const scopeCount = computed(() => {
  if (activeTab.value === 'links') return postsStore.linkGroups.length
  return postsStore.filteredCount || postsStore.displayPosts.length
})

// 지금 열어 둔 폴더 (없으면 null)
const selectedFolder = computed(() => foldersStore.byId(foldersStore.selected))

// 폴더나 챗봇 추천으로 좁혀진 상태 - 목록이 짧아 끊어 볼 이유가 없다
const isNarrowed = computed(
  () => !!postsStore.folderPostIds || postsStore.pickedIds.length > 0,
)

const visiblePosts = computed(() =>
  isNarrowed.value
    ? postsStore.displayPosts
    : postsStore.displayPosts.slice(0, visibleCount.value),
)
const canShowMore = computed(() =>
  isNarrowed.value
    ? false   // 좁혀진 목록은 전부 보여주므로 더보기가 필요 없다
    : visibleCount.value < postsStore.displayPosts.length || postsStore.hasMore,
)
// 좁혀 보기를 풀면 노출 범위를 처음부터 다시 센다.
// 안 그러면 폴더 글 몇 개만 세어둔 상태가 남아 전체로 돌아와도 그만큼만 보인다.
watch(isNarrowed, (now, before) => {
  if (before && !now) {
    visibleCount.value = 0
    revealMore()
  }
})

// 관리자가 게시글 탭을 볼 때만 - 지금 보이는 게시글들이 이미 SKALA 커리큘럼에 등록됐는지 배치 조회해서
// PostCard의 📚 퀵애드 아이콘 상태(추가/등록됨)에 반영. 일반 유저·다른 탭에서는 호출하지 않음
const curriculumStatusMap = ref({})

async function loadCurriculumStatus() {
  if (!authStore.effectiveIsAdmin || activeTab.value !== 'posts' || visiblePosts.value.length === 0) return
  try {
    const { data } = await fetchCurriculumStatus(visiblePosts.value.map((p) => p.id))
    const map = {}
    for (const entry of data ?? []) map[entry.postId] = { stage: entry.stage, subCategory: entry.subCategory }
    curriculumStatusMap.value = map
  } catch {
    curriculumStatusMap.value = {}
  }
}

watch(visiblePosts, loadCurriculumStatus)

// 필터/검색 등으로 목록이 처음부터 다시 조회될 때마다 노출 개수도 함께 리셋
watch(
  () => postsStore.resetToken,
  () => {
    visibleCount.value = 0
    revealMore()
  },
)

onMounted(async () => {
  if (!authStore.isAuthenticated) return
  // ?tab=links로 들어왔는데 스토어에 아직 반영 안 됐으면(새로고침 등) 맞춰줌 - 같은 세션에서 뒤로가기로
  // 돌아온 경우엔 이미 hasLink가 true라 여기서 다시 fetch를 유발하지 않음(직접 대입, setHasLink 아님)
  if (activeTab.value === 'links') {
    postsStore.hasLink = true
    await postsStore.ensureLinkGroupsLoaded()
  } else {
    // 캐시된 데이터를 그대로 쓴 경우(false 반환)는 fetchPosts가 호출되지 않아 resetToken이 안 바뀌므로
    // 위 watch가 노출량을 못 채움 - 여기서 직접 채워준다
    const didFetch = await postsStore.ensureLoaded()
    if (!didFetch) revealMore()
  }
  bookmarksStore.loadBookmarks()
})

// 카테고리 전환으로 층/기간 필터가 화면에서 사라지면, 안 보이는 상태로 몰래 걸려있지 않도록 값도 함께 초기화
watch(
  () => postsStore.category,
  () => {
    let changed = false
    if (!showCampusFilter.value && postsStore.campus) {
      postsStore.campus = null
      changed = true
    }
    if (!showDateFilter.value && postsStore.date) {
      postsStore.date = null
      changed = true
    }
    if (changed) postsStore.fetchPosts(true)
  },
)

function handleSearch(payload) {
  postsStore.setSearch(payload)
}

function selectSubTag(tagValue) {
  postsStore.setCategory(postsStore.category, postsStore.tag === tagValue ? null : tagValue)
}

async function loadMore() {
  // 이미 불러온 게시글 중 아직 안 보여준 게 있으면 API 호출 없이 그것부터 마저 노출
  if (visibleCount.value < postsStore.posts.length) {
    revealMore()
    return
  }
  if (postsStore.hasMore) {
    await postsStore.fetchPosts(false)
    revealMore()
  }
}

// 링크 모음 탭은 "더보기" 버튼 없이 스크롤이 바닥 근처에 닿으면 자동으로 다음 페이지를 이어서 불러옴.
// IntersectionObserver는 교차 상태가 "바뀔 때"만 콜백을 주므로, 한 번 불러온 뒤에도 sentinel이 계속
// 화면 안에 머물러 있으면(카드가 작아 한 페이지로도 뷰포트를 다 못 채우는 경우) 재호출되지 않는다.
// 그래서 콜백 안에서 sentinel이 실제로 화면을 벗어날 때까지 while로 계속 다음 페이지를 이어붙인다.
const scrollSentinel = ref(null)
let scrollObserver = null
let autoLoadingLinks = false

function isSentinelNearViewport() {
  const el = scrollSentinel.value
  if (!el) return false
  const rect = el.getBoundingClientRect()
  return rect.top < window.innerHeight + 400
}

async function autoLoadLinksWhileVisible() {
  if (autoLoadingLinks) return
  autoLoadingLinks = true
  try {
    while (postsStore.linkHasMore && !postsStore.linkGroupsLoading && isSentinelNearViewport()) {
      await postsStore.fetchLinkGroups(false)
    }
  } finally {
    autoLoadingLinks = false
  }
}

function teardownScrollObserver() {
  scrollObserver?.disconnect()
  scrollObserver = null
}

function setupScrollObserver() {
  teardownScrollObserver()
  if (activeTab.value !== 'links' || !scrollSentinel.value) return
  scrollObserver = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) autoLoadLinksWhileVisible()
    },
    { rootMargin: '400px' },
  )
  scrollObserver.observe(scrollSentinel.value)
}

watch([activeTab, scrollSentinel], () => nextTick(setupScrollObserver))
onUnmounted(teardownScrollObserver)

// 스크롤 방향에 따라 필터 헤더를 숨겼다 보여줬다 하는 방식은 position:sticky와 함께 쓰면 일부 모바일
// 브라우저에서 화면 중간 어딘가에 어정쩡하게 고정돼버리는 문제가 있어서 포기 - 대신 헤더는 항상 그 자리에
// sticky로 고정해두고, 스크롤을 많이 내렸을 때만 "맨 위로" 버튼을 띄워서 누르면 헤더 위치까지 쭉 올려줌
const showScrollTop = ref(false)
let scrollTopTicking = false

function handleScrollForTopButton() {
  if (scrollTopTicking) return
  scrollTopTicking = true
  requestAnimationFrame(() => {
    showScrollTop.value = window.scrollY > 400
    scrollTopTicking = false
  })
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => window.addEventListener('scroll', handleScrollForTopButton, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', handleScrollForTopButton))
</script>

<template>
  <AppLayout :padding-top="65">
    <AuthRequired v-if="!authStore.isAuthenticated" message="피드를 보려면 SKALA 교육생 인증이 필요합니다" />
    <template v-else>
      <SearchBar @search="handleSearch" />

      <div class="feed-sticky-filters">
        <div class="feed-tabs">
          <div class="feed-tab" :class="{ active: activeTab === 'posts' }" @click="selectTab('posts')">게시글</div>
          <div class="feed-tab" :class="{ active: activeTab === 'links' }" @click="selectTab('links')">
            🔗 링크 모음
          </div>
          <div class="feed-tab" :class="{ active: activeTab === 'curriculum' }" @click="selectTab('curriculum')">
            <img :src="skLogo" class="feed-tab-logo" alt="" /> SKALA 커리큘럼
          </div>
        </div>

        <template v-if="activeTab !== 'curriculum'">
          <CategoryFilter v-if="!showHidden" />


          <div v-if="!showHidden && (hasSubcategoryFilter || showCampusFilter || showDateFilter)" class="filter-combined-row">
            <div v-if="hasSubcategoryFilter" class="edu-category-filter">
              <span class="label">{{ subcategoryFilterLabel }}</span>
              <div class="pill" :class="{ active: !postsStore.tag }" @click="selectSubTag(null)">
                <span class="pill-label">전체</span
                ><span class="pill-count"
                  >({{
                    postsStore.hasLink
                      ? postsStore.linkCategoryCount(postsStore.category)
                      : postsStore.categoryCount(postsStore.category)
                  }})</span
                >
              </div>
              <div
                v-for="sub in activeCategoryTags"
                :key="sub.value"
                class="pill"
                :class="{ active: postsStore.tag === sub.value }"
                @click="selectSubTag(sub.value)"
              >
                <span class="pill-label">{{ sub.label }}</span
                ><span class="pill-count"
                  >({{ postsStore.hasLink ? postsStore.linkTagCount(sub.value) : postsStore.tagCount(sub.value) }})</span
                >
              </div>
            </div>

            <div v-if="showCampusFilter || showDateFilter" class="filter-row">
              <!-- 폴더를 보는 중엔 이 필터들도 실제로 안 쓰인다. 카테고리와 똑같이 흐리게 둔다. -->
              <div class="sub-filters" :class="{ dimmed: !!foldersStore.selected }">
                <CampusFilter v-if="showCampusFilter" />
                <DateFilter v-if="showDateFilter" />
              </div>
            </div>
          </div>

          <!-- 폴더는 탭이 아니라 카테고리와 같은 층위의 필터 - 어느 탭에서든 범위를 좁힌다 -->
          <!-- 내 폴더는 챗봇을 쓸 수 있는 사람에게 연다 (1차 베타 = 판교 5반).
                 범위는 서버가 정하므로 화면은 chatStore.canUse 하나만 본다 -->
            <AiFolderBar v-if="chatStore.canUse && !showHidden" />

          <!-- 동기화 시각과 정렬은 목록에 바로 붙는 정보라 필터들 아래, 게시글 바로 위에 둔다 -->
          <!-- 추천 칩 · 동기화 시각 · 정렬을 한 줄에 둔다. 추천 칩은 왼쪽에서 늘어나고
               동기화와 정렬은 오른쪽에 붙는다. -->
          <div class="sync-sort-row">
            <Transition name="pickchip">
              <div v-if="postsStore.pickedIds.length" class="pick-chip">
                <span class="pick-chip-text">
                  <template v-if="postsStore.pickQuery">'{{ postsStore.pickQuery }}'</template>
                  <template v-else>챗봇 추천</template>
                  관련 {{ postsStore.pickedIds.length }}개 보는 중
                </span>
                <button class="pick-chip-clear" @click="clearAsk = true">해제 ✕</button>
              </div>
              <!-- 폴더로 좁혀진 상태도 같은 자리에 같은 모양으로 보여준다.
                   표시가 없으면 "전체인데 몇 개만 보인다"로 느껴진다. -->
              <div v-else-if="selectedFolder" class="pick-chip folder-chip">
                <span class="folder-dot" :style="{ background: selectedFolder.color }"></span>
                <span class="pick-chip-text">
                  '{{ selectedFolder.name }}' {{ postsStore.displayPosts.length }}개 보는 중
                </span>
                <button class="pick-chip-clear" @click="foldersStore.selected = null">해제 ✕</button>
              </div>
              <!-- 좁혀 보는 중이 아닐 때도 지금 범위를 항상 알려준다 (해제할 게 없어 버튼은 없음) -->
              <div v-else class="pick-chip">
                <span class="pick-chip-text">{{ scopeIcon }} {{ scopeLabel }} {{ scopeCount }}개 보는 중 👀</span>
              </div>
            </Transition>

            <div class="sync-sort-right">
              <span v-if="postsStore.lastSyncedAt" class="last-sync">
                🕐 마지막 동기화: {{ formatRelativeTime(postsStore.lastSyncedAt) }}
              </span>
              <SortFilter />
            </div>
          </div>
        </template>
      </div>

      <CurriculumBoard v-if="activeTab === 'curriculum'" />

      <template v-else>
        <div v-if="showHidden" class="hidden-links-banner">
          🔒 관리자에게만 보이는 숨긴 링크입니다. 카드의 ♻️ 버튼으로 복원할 수 있어요.
        </div>

        <div
          v-if="
            showHidden
              ? postsStore.hiddenLinkGroupsLoading && postsStore.hiddenLinkGroups.length === 0
              : activeTab === 'links' && postsStore.linkGroupsLoading && postsStore.linkGroups.length === 0
          "
          class="link-gallery-grid"
          aria-hidden="true"
        >
          <LinkCardSkeleton v-for="n in 6" :key="n" />
        </div>
        <div
          v-else-if="!showHidden && activeTab !== 'links' && postsStore.loading && postsStore.posts.length === 0"
          class="post-list"
          aria-hidden="true"
        >
          <div class="post-card-skeleton" v-for="n in 3" :key="n">
            <SkeletonBlock width="70%" height="16px" />
            <SkeletonBlock width="100%" height="13px" />
            <SkeletonBlock width="90%" height="13px" />
            <SkeletonBlock width="40%" height="13px" />
          </div>
        </div>
        <div v-else-if="showHidden" class="link-gallery-grid">
          <LinkGalleryCard v-for="group in postsStore.hiddenLinkGroups" :key="group.url" :group="group" />
        </div>
        <div v-else-if="activeTab === 'links'" class="link-gallery-grid-wrapper">
          <div v-if="postsStore.linkFilterLoading" class="link-gallery-overlay">
            <span class="spinner spinner-lg" aria-hidden="true"></span>
          </div>
          <div class="link-gallery-grid">
            <LinkGalleryCard v-for="group in postsStore.linkGroups" :key="group.url" :group="group" />
            <div v-if="postsStore.linkHasMore" ref="scrollSentinel" class="scroll-sentinel" aria-hidden="true"></div>
          </div>
        </div>
        <div v-else class="feed-stage" :class="{ scanning: isScanning }">
          <!-- 챗봇이 글을 찾는 동안 목록 위를 보라색 줄이 훑는다.
               챗봇 패널의 PaperFlipLoader가 쓰는 스캔 줄과 같은 표현이라
               "오른쪽에서 하는 일이 왼쪽에 미치고 있다"가 설명 없이 읽힌다. -->
          <div v-if="isScanning" class="scan-line" aria-hidden="true"></div>
          <div v-if="isScanning" class="scan-label">
            <span class="scan-dot" aria-hidden="true"></span>
            {{ searchScope }}에서 검색 중
          </div>

          <!-- TransitionGroup은 목록 순서가 바뀔 때 각 카드가 실제로 미끄러져 이동한다(FLIP).
               추천 글이 아래에서 위로 올라오는 과정이 보여야 "정리됐다"는 느낌이 난다. -->
          <TransitionGroup tag="div" name="feed" class="post-list">
            <PostCard
              v-for="(post, i) in visiblePosts"
              :key="post.id"
              :style="{ '--scan-i': i }"
              :post="post"
              :highlight-keyword="postsStore.keyword"
              :curriculum-status="curriculumStatusMap[post.id] ?? null"
              :picked="postsStore.pickedIds.includes(post.id)"
            />
          </TransitionGroup>
        </div>

        <template v-if="showHidden">
          <div v-if="postsStore.hiddenLinkGroupsLoading && postsStore.hiddenLinkGroups.length > 0" class="loading-indicator">
            <span class="spinner" aria-hidden="true"></span> 불러오는 중...
          </div>
          <div v-else-if="postsStore.hiddenLinkGroups.length === 0" class="status-message">숨긴 링크가 없습니다.</div>
        </template>
        <template v-else-if="activeTab === 'links'">
          <div
            v-if="postsStore.linkGroupsLoading && !postsStore.linkFilterLoading && postsStore.linkGroups.length > 0"
            class="loading-indicator"
          >
            <span class="spinner" aria-hidden="true"></span> 불러오는 중...
          </div>
          <div v-else-if="postsStore.error" class="status-message error">{{ postsStore.error }}</div>
          <div v-else-if="postsStore.linkGroups.length === 0" class="status-message">
            링크가 달린 게시글이 없습니다.
          </div>
        </template>
        <template v-else>
          <div v-if="postsStore.loading && postsStore.posts.length > 0" class="loading-indicator">
            <span class="spinner" aria-hidden="true"></span> 불러오는 중...
          </div>
          <div v-else-if="postsStore.error" class="status-message error">{{ postsStore.error }}</div>
          <div v-else-if="postsStore.posts.length === 0 && postsStore.isFutureMonth" class="status-message">
            아직 시작되지 않은 달이에요. 작성된 게시글이 없습니다.
          </div>
          <div v-else-if="postsStore.posts.length === 0" class="status-message">게시글이 없습니다.</div>
        </template>

        <div v-if="canShowMore && !postsStore.loading && activeTab !== 'links'" class="load-more" @click="loadMore">
          더보기
        </div>
      </template>

      <button v-if="showScrollTop" class="scroll-top-btn" aria-label="맨 위로" @click="scrollToTop">
        <span class="scroll-top-icon">↑</span>
        <span class="scroll-top-label">맨 위로</span>
      </button>
    </template>

    <ConfirmDialog
      :open="clearAsk"
      :danger="false"
      title="추천을 해제할까요?"
      message="맨 위에 올려둔 추천 글이 원래 자리로 돌아가고, 피드가 기존 정렬 순서로 다시 정렬됩니다."
      confirm-label="해제"
      cancel-label="그대로 두기"
      @confirm="confirmClear"
      @cancel="clearAsk = false"
    />
  </AppLayout>
</template>

<style scoped>
/* 탭+카테고리+층/기간+내 폴더까지 쌓이면 이 블록이 화면 높이의 상당 부분을 차지한다.
   상단에 고정(sticky)해두면 스크롤할 때마다 게시글을 가려버리므로 고정하지 않는다.
   페이지 맨 위에서만 보이다가 다른 콘텐츠처럼 함께 흘러가고, 필터를 다시 만지고 싶으면
   우측 하단 "맨 위로" 버튼으로 돌아온다. */
.feed-sticky-filters {
  background: #fafafa;
  padding-top: 4px;
}

/* 스크롤을 많이 내렸을 때만 뜨는 "맨 위로" 버튼 - 누르면 헤더가 있는 맨 위까지 부드럽게 스크롤.
   반투명 + blur로 뒤 게시글이 은은하게 비치게 해서 진한 단색보다 콘텐츠를 덜 가리게 함 */
.scroll-top-btn {
  position: fixed;
  right: 20px;
  bottom: 92px; /* 우하단 AI 챗봇 런처(bottom 24px, 높이 50px) 위로 띄움 */
  z-index: 50;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: rgba(74, 63, 143, 0.72);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  color: #ffffff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(26, 26, 46, 0.2);
}

.scroll-top-btn:hover {
  background: rgba(108, 92, 231, 0.8);
}

.scroll-top-icon {
  font-size: 16px;
  line-height: 1;
}

.scroll-top-label {
  font-size: 9px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.feed-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
  margin-bottom: 16px;
}

.feed-tab {
  padding: 10px 2px 12px;
  font-size: 14px;
  font-weight: 600;
  color: #636e72;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}

.feed-tab.active {
  color: #4a3f8f;
  border-bottom-color: #4a3f8f;
}

/* 챗봇 추천으로 목록 순서가 바뀔 때 카드가 제자리를 찾아 미끄러진다.
   기존 UI가 쓰는 감속 커브(0.22,1,0.36,1)를 그대로 써서 이질감을 줄였다. */
.feed-move {
  transition: transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}

@media (prefers-reduced-motion: reduce) {
  .feed-move {
    transition: none;
  }
}

/* 추천 글이 위에서 차례로 나타나고, 등장 직후 보라 테두리가 한 번 반짝 */
.ai-pick {
  border-radius: 16px;
  animation: pick-in 0.55s cubic-bezier(0.2, 0.9, 0.3, 1) calc(var(--i) * 160ms + 150ms) both,
    pick-glow 1.4s ease-out calc(var(--i) * 160ms + 500ms) both;
}

@keyframes pick-in {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: none; }
}

@keyframes pick-glow {
  0% { box-shadow: 0 0 0 0 rgba(108, 92, 231, 0.55); }
  100% { box-shadow: 0 0 0 14px rgba(108, 92, 231, 0); }
}

.feed-tab-logo {
  width: 18px;
  height: 18px;
  object-fit: contain;
  vertical-align: -6px;
}

.hidden-links-banner {
  margin-bottom: 16px;
  padding: 10px 16px;
  border-radius: 10px;
  background: #fff4e0;
  color: #8a5a00;
  font-size: 13px;
  font-weight: 600;
}

.link-gallery-grid-wrapper {
  position: relative;
}

.link-gallery-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

/* 카테고리/필터 전환 시 카드 목록을 유지한 채 살짝 dim 처리 + 스피너만 겹쳐 보여줌 -
   목록이 통째로 비었다 다시 채워지는 깜빡임 없이 즉시 전환되는 것처럼 느껴지게 함 */
.link-gallery-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(250, 250, 250, 0.6);
  border-radius: 12px;
}

.spinner-lg {
  width: 28px;
  height: 28px;
  border-width: 3px;
}

.scroll-sentinel {
  grid-column: 1 / -1;
  height: 1px;
}

@media (max-width: 768px) {
  .link-gallery-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 좁은 폰 화면(360~430px)에서도 스크롤을 줄이기 위해 2열을 유지하고 여백만 살짝 좁힘 */
@media (max-width: 480px) {
  .link-gallery-grid {
    gap: 10px;
  }
}

/* 유형·분류 필터(edu-category-filter)와 층·기간 필터(filter-row)를 한 줄에 나란히 배치 -
   화면이 좁으면 flex-wrap으로 자연스럽게 다음 줄로 넘어감 */
.filter-combined-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.edu-category-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 12px;
  padding: 6px;
  width: fit-content;
}

.edu-category-filter .label {
  font-size: 13px;
  color: #636e72;
  padding: 0 2px 0 6px;
  font-weight: 600;
  white-space: nowrap;
}

.edu-category-filter .pill {
  display: flex;
  align-items: baseline;
  gap: 1px;
  padding: 6px 12px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

/* 화면이 좁아져도 유형/기간 필터가 최대한 한 줄에 붙어있도록 버튼을 한 번 더 축소 */
@media (max-width: 1024px) {
  .edu-category-filter .label {
    font-size: 12px;
    padding: 0 2px 0 4px;
  }

  .edu-category-filter .pill {
    padding: 5px 9px;
    font-size: 12px;
  }
}

/* 모바일은 태그 라벨이 "학습 및 스터디매칭" 같이 길어서 위 1024px 축소만으로는 2줄에 안 들어감 -
   CategoryFilter와 같은 방식으로 라벨/카운트 폰트를 분리해서 카운트만 한 번 더 줄임(공백은 이미
   템플릿에서 태그를 붙여써서 없앤 상태) */
@media (max-width: 768px) {
  .edu-category-filter {
    gap: 4px 6px;
    padding: 7px 9px;
  }

  .edu-category-filter .pill {
    padding: 6px 7px;
  }

  .edu-category-filter .pill-count {
    font-size: 10px;
    opacity: 0.8;
  }
}

.edu-category-filter .pill.active {
  background: #f1eefc;
  color: #4a3f8f;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

/* 모바일은 층/기간이 나란히 한 줄에 안 들어가서 각자 박스로 세로로 쌓이면 분리된 느낌이 강했음 -
   이 줄(.filter-row) 자체를 하나의 박스로 만들고, 안에 있는 CampusFilter/DateFilter 컴포넌트의
   자체 박스 스타일(배경/테두리/radius)은 지워서 내용물만 남긴 뒤 세로로 쌓는다. 층 쪽에 아래
   테두리를 하나 그어서 구분선처럼 보이게 함(층이 없으면 그 border-bottom도 같이 없어져서 문제없음) */
@media (max-width: 768px) {
  /* 위/아래 여백은 박스 바깥쪽(.filter-row)에서 하위 분류 박스와 같은 7px 9px로 한 번만 주고,
     안쪽 두 구간(층/기간)은 자체 여백 없이 구분선 주변에만 딱 필요한 만큼만 최소로 띄운다 -
     안팎에 여백이 두 겹으로 쌓이면 하위 분류 박스보다 세로로 훨씬 길어짐. 폭은 카테고리 박스와
     맞추기 위해 fit-content 대신 100%로 통일 */
  .filter-row {
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    background: #ffffff;
    border: 1px solid rgba(26, 26, 46, 0.08);
    border-radius: 12px;
    padding: 7px 9px;
    width: 100%;
  }

  .filter-row :deep(.campus-filter),
  .filter-row :deep(.date-filter) {
    background: transparent;
    border: none;
    border-radius: 0;
    padding: 0;
    width: auto;
  }

  .filter-row :deep(.campus-filter) {
    padding-bottom: 3px;
    border-bottom: 1px solid rgba(26, 26, 46, 0.08);
  }

  .filter-row :deep(.date-filter) {
    padding-top: 3px;
  }

  /* 바로 위 하위 분류(edu-category-filter) 박스와 높이/여백을 맞추기 위해 pill 크기도 같은
     기준(padding 6px 7px, font 12px)으로 축소 */
  .filter-row :deep(.campus-filter .pill),
  .filter-row :deep(.date-filter .pill) {
    padding: 6px 7px;
    font-size: 12px;
  }

  /* "층"(1글자)과 "기간"(2글자) 라벨 폭이 달라서 뒤에 오는 "전체" 필터 버튼 시작 위치가
     줄마다 어긋나 보였음 - 두 라벨에 같은 최소 폭을 줘서 버튼 시작 위치를 맞춘다 */
  .filter-row :deep(.campus-filter .label),
  .filter-row :deep(.date-filter .label) {
    display: inline-block;
    min-width: 54px;
  }
}

/* 챗봇이 글을 찾는 동안의 피드 상태 ------------------------------------ */
.feed-stage {
  position: relative;
}

/* 검색 중에는 목록을 흐리게 깔아두고 스캔 줄만 또렷하게 둔다 */
.feed-stage.scanning .post-list {
  opacity: 0.45;
  transition: opacity 0.25s ease;
}

.scan-line {
  position: absolute;
  left: -4px;
  right: -4px;
  top: 0;
  height: 2px;
  z-index: 4;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, #6c5ce7 18%, #6c5ce7 82%, transparent);
  box-shadow: 0 0 14px rgba(108, 92, 231, 0.75);
  animation: scan-sweep 1.6s cubic-bezier(0.45, 0, 0.55, 1) infinite;
}

@keyframes scan-sweep {
  0% { top: 0; opacity: 0; }
  12% { opacity: 1; }
  88% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

/* 스캔 중 - 카드가 위에서부터 차례로 잠깐 밝아지며 "훑고 지나가는" 느낌을 만든다.
   스캔 줄 하나만으로는 장식처럼 보여서, 카드가 반응해야 읽히는 대상이라는 게 전달된다. */
.feed-stage.scanning .post-list > * {
  animation: scan-touch 1.6s ease-in-out infinite;
  animation-delay: calc(var(--scan-i, 0) * 90ms);
}

@keyframes scan-touch {
  0%, 100% { opacity: 0.4; transform: none; }
  18% { opacity: 1; transform: translateX(3px); }
  36% { opacity: 0.55; transform: none; }
}

/* 몇 개를 보고 있는지 숫자로 알려준다 - 막연한 로딩이 아니라 작업 중임이 분명해진다 */
.scan-label {
  position: sticky;
  top: 12px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 10px;
  padding: 7px 13px;
  border-radius: 999px;
  background: rgba(74, 63, 143, 0.94);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(74, 63, 143, 0.3);
}

.scan-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ffffff;
  animation: scan-blink 1s ease-in-out infinite;
}

@keyframes scan-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

@media (prefers-reduced-motion: reduce) {
  .feed-stage.scanning .post-list > *,
  .scan-dot {
    animation: none;
  }
}

/* 추천 상태 칩 - 지금 무엇을 보고 있고 어떻게 끄는지 알려준다 */
.pick-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 8px 0 14px;
  border: 1px solid rgba(108, 92, 231, 0.28);
  border-radius: 10px;
  background: #f1eefc;
}

.sub-filters {
  display: contents;   /* 감싸기만 하고 기존 가로 배치를 그대로 둔다 */
}

.sub-filters.dimmed :deep(.chip),
.sub-filters.dimmed :deep(button) {
  opacity: 0.42;
}

.sub-filters.dimmed:hover :deep(.chip),
.sub-filters.dimmed:hover :deep(button) {
  opacity: 1;
}

.folder-dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  flex: none;
}

.pick-chip-text {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #4a3f8f;
  word-break: keep-all;
}

.pick-chip-clear {
  flex: none;
  height: 26px;
  padding: 0 10px;
  border: 1px solid rgba(74, 63, 143, 0.3);
  border-radius: 7px;
  background: #ffffff;
  color: #4a3f8f;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.pick-chip-clear:hover {
  background: rgba(74, 63, 143, 0.07);
}

.pickchip-enter-active,
.pickchip-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.pickchip-enter-from,
.pickchip-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (prefers-reduced-motion: reduce) {
  .scan-line {
    animation: none;
    top: 0;
  }

  .feed-stage.scanning .post-list,
  .pickchip-enter-active,
  .pickchip-leave-active {
    transition: none;
  }
}

.sync-sort-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

/* 추천 칩이 있으면 그 칩이 왼쪽을 차지하고 동기화·정렬이 오른쪽으로 밀린다.
   칩이 없을 때는 동기화가 왼쪽 끝을 맡는다. */
/* 남는 가로 공간을 칩이 채워서 동기화 버튼 바로 왼쪽까지 이어진다 */
.sync-sort-row .pick-chip {
  flex: 1;
  min-width: 0;
}

/* 동기화 시각과 정렬은 한 덩어리로 묶어 항상 오른쪽 끝에 붙여둔다 */
.sync-sort-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.last-sync {
  display: inline-flex;
  align-items: center;
  height: 38px;
  background: #ffffff;
  border-radius: 10px;
  padding: 0 16px;
  font-size: 13px;
  color: #636e72;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.05);
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.post-card-skeleton {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px 28px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.06);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.status-message {
  margin-top: 24px;
  text-align: center;
  color: #636e72;
  font-size: 14px;
}

.status-message.error {
  color: #e01e5a;
}

.loading-indicator {
  margin-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #636e72;
  font-size: 14px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(74, 63, 143, 0.2);
  border-top-color: #4a3f8f;
  border-radius: 50%;
  animation: spinner-rotate 0.7s linear infinite;
}

@keyframes spinner-rotate {
  to {
    transform: rotate(360deg);
  }
}

.load-more {
  margin-top: 24px;
  text-align: center;
  padding: 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.1);
  color: #4a3f8f;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
}
</style>
