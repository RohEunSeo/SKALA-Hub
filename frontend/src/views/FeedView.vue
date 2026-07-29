<script setup>
// 게시글 피드(목록) 화면
import { computed, onMounted, watch } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import SearchBar from '../components/SearchBar.vue'
import CategoryFilter from '../components/CategoryFilter.vue'
import SortFilter from '../components/SortFilter.vue'
import CampusFilter from '../components/CampusFilter.vue'
import DateFilter from '../components/DateFilter.vue'
import PostCard from '../components/PostCard.vue'
import { usePostsStore } from '../stores/posts'
import { useBookmarksStore } from '../stores/bookmarks'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/relativeTime'
import { CATEGORIES } from '../constants/categories'

// 층(4층/5층) 필터가 의미 있는 카테고리 - 자격증·취업/교수님/기타/교육생 서비스는 층 구분이 무의미해서 제외
const CAMPUS_CATEGORIES = ['개발 툴·환경', '학습자료']
// 기간 필터가 의미 있는 카테고리 - 자격증·취업/교수님/기타/교육생 서비스는 게시글 수가 적어 기간 필터 실익이 낮음
const DATE_CATEGORIES = ['개발 툴·환경', '학습자료']

const postsStore = usePostsStore()
const bookmarksStore = useBookmarksStore()
const authStore = useAuthStore()

const eduServiceTags = computed(
  () => CATEGORIES.find((cat) => cat.value === '교육생 서비스')?.tags ?? [],
)
const isEduService = computed(() => postsStore.category === '교육생 서비스')
const showCampusFilter = computed(() => !postsStore.category || CAMPUS_CATEGORIES.includes(postsStore.category))
const showDateFilter = computed(() => !postsStore.category || DATE_CATEGORIES.includes(postsStore.category))

onMounted(() => {
  if (!authStore.isAuthenticated) return
  postsStore.fetchPosts(true)
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

function selectEduTag(tagValue) {
  postsStore.setCategory('교육생 서비스', postsStore.tag === tagValue ? null : tagValue)
}

function loadMore() {
  postsStore.fetchPosts(false)
}
</script>

<template>
  <AppLayout>
    <AuthRequired v-if="!authStore.isAuthenticated" message="피드를 보려면 SKALA 교육생 인증이 필요합니다" />
    <template v-else>
      <SearchBar @search="handleSearch" />
      <CategoryFilter />

      <div v-if="isEduService" class="edu-category-filter">
        <span class="label">🗂️ 분류 : </span>
        <div class="pill" :class="{ active: !postsStore.tag }" @click="selectEduTag(null)">
          전체 ({{ postsStore.categoryCount('교육생 서비스') }})
        </div>
        <div
          v-for="sub in eduServiceTags"
          :key="sub.value"
          class="pill"
          :class="{ active: postsStore.tag === sub.value }"
          @click="selectEduTag(sub.value)"
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

      <div class="post-list">
        <PostCard
          v-for="post in postsStore.posts"
          :key="post.id"
          :post="post"
          :highlight-keyword="postsStore.keyword"
        />
      </div>

      <div v-if="postsStore.loading" class="status-message">불러오는 중...</div>
      <div v-else-if="postsStore.error" class="status-message error">{{ postsStore.error }}</div>
      <div v-else-if="postsStore.posts.length === 0 && postsStore.isFutureMonth" class="status-message">
        아직 시작되지 않은 달이에요. 작성된 게시글이 없습니다.
      </div>
      <div v-else-if="postsStore.posts.length === 0" class="status-message">게시글이 없습니다.</div>

      <div v-if="postsStore.hasMore && !postsStore.loading" class="load-more" @click="loadMore">더보기</div>
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
