<script setup>
// 게시글 피드(목록) 화면
import { computed, onMounted, ref, watch } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import SearchBar from '../components/SearchBar.vue'
import CategoryFilter from '../components/CategoryFilter.vue'
import SortFilter from '../components/SortFilter.vue'
import CampusFilter from '../components/CampusFilter.vue'
import DateFilter from '../components/DateFilter.vue'
import PostCard from '../components/PostCard.vue'
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

const postsStore = usePostsStore()
const bookmarksStore = useBookmarksStore()
const authStore = useAuthStore()

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
</script>

<template>
  <AppLayout :padding-top="65">
    <AuthRequired v-if="!authStore.isAuthenticated" message="피드를 보려면 SKALA 교육생 인증이 필요합니다" />
    <template v-else>
      <SearchBar @search="handleSearch" />
      <CategoryFilter />

      <div v-if="hasSubcategoryFilter" class="edu-category-filter">
        <span class="label">🗂️ 분류 : </span>
        <div class="pill" :class="{ active: !postsStore.tag }" @click="selectSubTag(null)">
          전체 ({{ postsStore.categoryCount(postsStore.category) }})
        </div>
        <div
          v-for="sub in activeCategoryTags"
          :key="sub.value"
          class="pill"
          :class="{ active: postsStore.tag === sub.value }"
          @click="selectSubTag(sub.value)"
        >
          {{ sub.label }} ({{ postsStore.tagCount(sub.value) }})
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

      <div v-if="postsStore.loading && postsStore.posts.length === 0" class="post-list" aria-hidden="true">
        <div class="post-card-skeleton" v-for="n in 3" :key="n">
          <SkeletonBlock width="70%" height="16px" />
          <SkeletonBlock width="100%" height="13px" />
          <SkeletonBlock width="90%" height="13px" />
          <SkeletonBlock width="40%" height="13px" />
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

      <div v-if="postsStore.loading && postsStore.posts.length > 0" class="status-message">불러오는 중...</div>
      <div v-else-if="postsStore.error" class="status-message error">{{ postsStore.error }}</div>
      <div v-else-if="postsStore.posts.length === 0 && postsStore.isFutureMonth" class="status-message">
        아직 시작되지 않은 달이에요. 작성된 게시글이 없습니다.
      </div>
      <div v-else-if="postsStore.posts.length === 0" class="status-message">게시글이 없습니다.</div>

      <div v-if="canShowMore && !postsStore.loading" class="load-more" @click="loadMore">더보기</div>
    </template>
  </AppLayout>
</template>

<style scoped>
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
