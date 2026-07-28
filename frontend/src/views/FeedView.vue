<script setup>
// 게시글 피드(목록) 화면
import { onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import SearchBar from '../components/SearchBar.vue'
import CategoryFilter from '../components/CategoryFilter.vue'
import DateFilter from '../components/DateFilter.vue'
import PostCard from '../components/PostCard.vue'
import { usePostsStore } from '../stores/posts'
import { useBookmarksStore } from '../stores/bookmarks'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/relativeTime'

const postsStore = usePostsStore()
const bookmarksStore = useBookmarksStore()
const authStore = useAuthStore()

onMounted(() => {
  if (!authStore.isAuthenticated) return
  postsStore.fetchPosts(true)
  bookmarksStore.loadBookmarks()
})

function handleSearch(payload) {
  postsStore.setSearch(payload)
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
      <DateFilter />

      <div v-if="postsStore.lastSyncedAt" class="last-sync">
        🕐 마지막 동기화: {{ formatRelativeTime(postsStore.lastSyncedAt) }}
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
      <div v-else-if="postsStore.posts.length === 0" class="status-message">게시글이 없습니다.</div>

      <div v-if="postsStore.hasMore && !postsStore.loading" class="load-more" @click="loadMore">더보기</div>
    </template>
  </AppLayout>
</template>

<style scoped>
.last-sync {
  display: inline-block;
  background: #ffffff;
  border-radius: 10px;
  padding: 9px 16px;
  font-size: 13px;
  color: #636e72;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.05);
  margin-bottom: 20px;
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
