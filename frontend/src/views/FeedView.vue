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

function selectTab(tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  postsStore.setHasLink(tab === 'links' ? true : null)
}

const activeCategoryTags = computed(
  () => CATEGORIES.find((cat) => cat.value === postsStore.category)?.tags ?? [],
)
const hasSubcategoryFilter = computed(
  () => SUBCATEGORY_FILTER_CATEGORIES.includes(postsStore.category) && activeCategoryTags.value.length > 0,
)
const showCampusFilter = computed(() => !postsStore.category || CAMPUS_CATEGORIES.includes(postsStore.category))
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

// 링크 모음 탭 - 카드가 작아서 "체감 스크롤 길이" 제한 없이 불러온 게시글 전부를 바로 펼쳐 보여줌.
// post.links는 슬랙이 미리보기 카드를 만들어준 링크(attachments)뿐 아니라 본문에 텍스트로만 붙은 링크도
// 포함한 전체 목록 - 게시글 하나에 링크가 여러 개면 링크마다 카드 1개씩 나뉜다
const linkCards = computed(() =>
  postsStore.posts.flatMap((post) =>
    (post.links ?? []).map((attachment) => ({ post, attachment })),
  ),
)

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
  if (activeTab.value === 'links' && postsStore.hasLink !== true) {
    postsStore.hasLink = true
  }
  // 캐시된 데이터를 그대로 쓴 경우(false 반환)는 fetchPosts가 호출되지 않아 resetToken이 안 바뀌므로
  // 위 watch가 노출량을 못 채움 - 여기서 직접 채워준다
  const didFetch = await postsStore.ensureLoaded()
  if (!didFetch) revealMore()
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
    while (postsStore.hasMore && !postsStore.loading && isSentinelNearViewport()) {
      await postsStore.fetchPosts(false)
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

        <CategoryFilter />

        <div v-if="hasSubcategoryFilter" class="edu-category-filter">
          <span class="label">🗂️ 분류 : </span>
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

        <div class="sync-sort-row">
          <span v-if="postsStore.lastSyncedAt" class="last-sync">
            🕐 마지막 동기화: {{ formatRelativeTime(postsStore.lastSyncedAt) }}
          </span>
          <SortFilter />
        </div>
      </div>

      <div v-if="postsStore.loading && postsStore.posts.length === 0" class="post-list" aria-hidden="true">
        <div class="post-card-skeleton" v-for="n in 3" :key="n">
          <SkeletonBlock width="70%" height="16px" />
          <SkeletonBlock width="100%" height="13px" />
          <SkeletonBlock width="90%" height="13px" />
          <SkeletonBlock width="40%" height="13px" />
        </div>
      </div>
      <div v-else-if="activeTab === 'links'" class="link-gallery-grid">
        <LinkGalleryCard
          v-for="card in linkCards"
          :key="`${card.post.id}-${card.attachment.titleLink || card.attachment.fromUrl}`"
          :post="card.post"
          :attachment="card.attachment"
        />
        <div v-if="postsStore.hasMore" ref="scrollSentinel" class="scroll-sentinel" aria-hidden="true"></div>
      </div>
      <div v-else class="post-list">
        <PostCard
          v-for="post in visiblePosts"
          :key="post.id"
          :post="post"
          :highlight-keyword="postsStore.keyword"
        />
      </div>

      <div v-if="postsStore.loading && postsStore.posts.length > 0" class="status-message">불러오는 중...</div>
      <div v-else-if="postsStore.error" class="status-message error">{{ postsStore.error }}</div>
      <div v-else-if="postsStore.posts.length === 0 && postsStore.isFutureMonth" class="status-message">
        아직 시작되지 않은 달이에요. 작성된 게시글이 없습니다.
      </div>
      <div v-else-if="activeTab === 'links' && linkCards.length === 0" class="status-message">
        링크가 달린 게시글이 없습니다.
      </div>
      <div v-else-if="postsStore.posts.length === 0" class="status-message">게시글이 없습니다.</div>

      <div
        v-if="canShowMore && !postsStore.loading && activeTab !== 'links'"
        class="load-more"
        @click="loadMore"
      >
        더보기
      </div>
    </template>
  </AppLayout>
</template>

<style scoped>
/* 링크 갤러리처럼 긴 목록을 스크롤할 때도 탭/카테고리/층·기간 필터는 화면 상단에 계속 붙어있도록 고정 -
   그래야 스크롤을 내린 상태에서도 필터를 다시 위로 올라가지 않고 바로 누를 수 있다 */
.feed-sticky-filters {
  position: sticky;
  top: 0;
  z-index: 5;
  background: #fafafa;
  padding-top: 4px;
}

/* 모바일 햄버거 메뉴 버튼(고정, 왼쪽 위 14px)과 겹치지 않도록 그 아래로 내려서 고정 */
@media (max-width: 768px) {
  .feed-sticky-filters {
    top: 60px;
  }
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

.link-gallery-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
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

@media (max-width: 480px) {
  .link-gallery-grid {
    grid-template-columns: 1fr;
  }
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
  margin-bottom: 16px;
}

.edu-category-filter .label {
  font-size: 13px;
  color: #636e72;
  padding: 0 2px 0 6px;
  font-weight: 600;
  white-space: nowrap;
}

.edu-category-filter .pill {
  padding: 8px 16px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
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
  margin-bottom: 16px;
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
