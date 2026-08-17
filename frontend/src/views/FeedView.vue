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
import LinkGalleryCard from '../components/LinkGalleryCard.vue'
import LinkCardSkeleton from '../components/LinkCardSkeleton.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import { usePostsStore } from '../stores/posts'
import { useBookmarksStore } from '../stores/bookmarks'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/relativeTime'
import { CATEGORIES } from '../constants/categories'

// 층(4층/5층) 필터가 의미 있는 카테고리 - 자격증·취업/교수님/기타/교육생 서비스는 층 구분이 무의미해서 제외
const CAMPUS_CATEGORIES = ['개발 툴·환경', '학습자료']
// 기간 필터가 의미 있는 카테고리 - 자격증·취업/교수님/기타/교육생 서비스는 게시글 수가 적어 기간 필터 실익이 낮음
const DATE_CATEGORIES = ['개발 툴·환경', '학습자료']
// "🗂️ 분류" 하위 태그 필터 버튼을 보여줄 카테고리 - 학습자료는 사이드바 필터만 쓰고 있어 제외
const SUBCATEGORY_FILTER_CATEGORIES = ['교육생 서비스', '기타']

const route = useRoute()
const postsStore = usePostsStore()
const bookmarksStore = useBookmarksStore()
const authStore = useAuthStore()

// 상단 탭("게시글"/"🔗 링크 모음") - 뷰 로컬 상태, 카테고리/층/기간 필터는 스토어에서 그대로 공유됨.
// 링크 모음 카드의 "게시글 보러가기"로 상세 페이지에 갔다가 뒤로가기로 돌아올 때 ?tab=links가 붙어 오면 링크 탭으로 복원
const activeTab = ref(route.query.tab === 'links' ? 'links' : 'posts')

// 사이드바 카테고리 클릭처럼 이 화면을 벗어나지 않고 store.hasLink가 바뀌는 경우, 탭 UI도 함께 전환
watch(
  () => postsStore.hasLink,
  (val) => {
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
  postsStore.setHasLink(tab === 'links' ? true : null)
}

// 링크 탭에서 관리자가 "숨김" 정렬 옵션을 켠 상태 - 이때는 카테고리/유형/기간 필터 대신 숨긴 링크 갤러리만 보여줌
const showHidden = computed(() => activeTab.value === 'links' && postsStore.showHiddenLinks)

const activeCategoryTags = computed(
  () => CATEGORIES.find((cat) => cat.value === postsStore.category)?.tags ?? [],
)
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
const WEIGHT_BUDGET = 90

function estimateWeight(post) {
  const textWeight = (post.content?.length ?? 0) / 40
  const imageCount = post.files?.filter((file) => file.isImage).length ?? 0
  const otherFileCount = (post.files?.length ?? 0) - imageCount
  const attachmentCount = post.attachments?.length ?? 0
  return 6 + textWeight + imageCount * 12 + otherFileCount * 3 + attachmentCount * 8
}

// 이미 불러온 게시글 중 예산이 남아있는 만큼만 추가로 노출 (API 재조회 없이 로컬에서 처리)
function revealMore() {
  let budget = WEIGHT_BUDGET
  while (visibleCount.value < postsStore.posts.length) {
    const next = postsStore.posts[visibleCount.value]
    visibleCount.value += 1
    budget -= estimateWeight(next)
    if (budget <= 0) break
  }
}

const visiblePosts = computed(() => postsStore.posts.slice(0, visibleCount.value))
const canShowMore = computed(() => visibleCount.value < postsStore.posts.length || postsStore.hasMore)

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
        </div>

        <CategoryFilter v-if="!showHidden" />

        <div v-if="!showHidden && (hasSubcategoryFilter || showCampusFilter || showDateFilter)" class="filter-combined-row">
          <div v-if="hasSubcategoryFilter" class="edu-category-filter">
            <span class="label">{{ subcategoryFilterLabel }}</span>
            <div class="pill" :class="{ active: !postsStore.tag }" @click="selectSubTag(null)">
              전체
              ({{
                postsStore.hasLink
                  ? postsStore.linkCategoryCount(postsStore.category)
                  : postsStore.categoryCount(postsStore.category)
              }})
            </div>
            <div
              v-for="sub in activeCategoryTags"
              :key="sub.value"
              class="pill"
              :class="{ active: postsStore.tag === sub.value }"
              @click="selectSubTag(sub.value)"
            >
              {{ sub.label }}
              ({{ postsStore.hasLink ? postsStore.linkTagCount(sub.value) : postsStore.tagCount(sub.value) }})
            </div>
          </div>

          <div v-if="showCampusFilter || showDateFilter" class="filter-row">
            <CampusFilter v-if="showCampusFilter" />
            <DateFilter v-if="showDateFilter" />
          </div>
        </div>

        <div class="sync-sort-row">
          <span v-if="postsStore.lastSyncedAt" class="last-sync">
            🕐 마지막 동기화: {{ formatRelativeTime(postsStore.lastSyncedAt) }}
          </span>
          <SortFilter />
        </div>
      </div>

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
      <div v-else class="post-list">
        <PostCard
          v-for="post in visiblePosts"
          :key="post.id"
          :post="post"
          :highlight-keyword="postsStore.keyword"
        />
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

      <button v-if="showScrollTop" class="scroll-top-btn" aria-label="맨 위로" @click="scrollToTop">
        <span class="scroll-top-icon">↑</span>
        <span class="scroll-top-label">맨 위로</span>
      </button>
    </template>
  </AppLayout>
</template>

<style scoped>
/* 데스크톱은 화면이 넉넉해서 탭/카테고리/필터를 화면 상단에 계속 붙여둠(position:sticky) - 스크롤을
   내린 상태에서도 필터를 바로 누를 수 있어 편리하고, 화면이 넓어 콘텐츠를 가리는 문제도 없다 */
.feed-sticky-filters {
  position: sticky;
  top: 0;
  z-index: 5;
  background: #fafafa;
  padding-top: 4px;
}

/* 모바일은 이 블록이 화면의 상당 부분을 차지해서, sticky로 고정해두면 스크롤할 때마다 게시글/링크를
   가려버림. 그래서 모바일에서는 고정을 아예 풀고 페이지 맨 위에서만 보이다가 스크롤하면 다른 콘텐츠처럼
   함께 흘러가 버리게 두고, 다시 필터를 만지고 싶으면 우측 하단 "맨 위로" 버튼으로 여기까지 올라오게 함 */
@media (max-width: 768px) {
  .feed-sticky-filters {
    position: static;
  }
}

/* 스크롤을 많이 내렸을 때만 뜨는 "맨 위로" 버튼 - 누르면 헤더가 있는 맨 위까지 부드럽게 스크롤.
   반투명 + blur로 뒤 게시글이 은은하게 비치게 해서 진한 단색보다 콘텐츠를 덜 가리게 함 */
.scroll-top-btn {
  position: fixed;
  right: 20px;
  bottom: 24px;
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

.sync-sort-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.sync-sort-row :deep(.sort-dropdown-wrapper) {
  margin-left: auto;
}

.last-sync {
  display: inline-block;
  background: #ffffff;
  border-radius: 10px;
  padding: 9px 16px;
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
