<script setup>
// 마이페이지 - 프로필/통계/내가 올린 글·저장한 글
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import { useAuthStore } from '../stores/auth'
import { useBookmarksStore } from '../stores/bookmarks'
import { removeBookmark } from '../api/bookmarks'
import { fetchMyStats, fetchMyPosts } from '../api/mypage'
import { formatRelativeTime } from '../utils/relativeTime'
import { stripSlackMarkdown } from '../utils/renderSlackText'
import { CATEGORIES } from '../constants/categories'

const PAGE_SIZE = 5

const router = useRouter()
const authStore = useAuthStore()
const bookmarksStore = useBookmarksStore()
const stats = ref(null)
const activeTab = ref('posts')
const posts = ref([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)

function categoryLabel(value) {
  return CATEGORIES.find((cat) => cat.value === value)?.shortLabel ?? value
}

async function loadStats() {
  const { data } = await fetchMyStats()
  stats.value = data
}

async function loadPosts() {
  loading.value = true
  try {
    const { data } = await fetchMyPosts(activeTab.value, page.value, PAGE_SIZE)
    posts.value = data.content
    totalPages.value = data.totalPages
  } finally {
    loading.value = false
  }
}

function goToPost(postId) {
  router.push({ name: 'post-detail', params: { id: postId } })
}

async function unsave(postId) {
  await removeBookmark(postId)
  bookmarksStore.setBookmarks(bookmarksStore.bookmarkedPostIds.filter((id) => id !== postId))
  posts.value = posts.value.filter((post) => post.id !== postId)
  if (stats.value) {
    stats.value = { ...stats.value, savedCount: Math.max(0, stats.value.savedCount - 1) }
  }
}

function setTab(tab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  page.value = 0
  loadPosts()
}

function prevPage() {
  if (page.value > 0) {
    page.value -= 1
    loadPosts()
  }
}

function nextPage() {
  if (page.value + 1 < totalPages.value) {
    page.value += 1
    loadPosts()
  }
}

onMounted(() => {
  if (!authStore.isAuthenticated) return
  loadStats()
  loadPosts()
})
</script>

<template>
  <AppLayout>
    <AuthRequired v-if="!authStore.isAuthenticated" message="마이페이지를 보려면 SKALA 교육생 인증이 필요합니다" />
    <template v-else>
      <div class="profile-header">
        <img
          v-if="authStore.user?.profileImg"
          class="avatar avatar-img"
          :src="authStore.user.profileImg"
          :alt="authStore.user.name"
        />
        <div v-else class="avatar">{{ authStore.user?.name?.charAt(0) }}</div>
        <div>
          <div class="profile-name">{{ authStore.user?.name }}</div>
          <div class="profile-meta">
            {{
              [authStore.user?.cohort, authStore.user?.campus, authStore.user?.classNum]
                .filter(Boolean)
                .join(' ')
            }}
          </div>
        </div>
      </div>

      <div v-if="stats" class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon">📌</div>
          <div class="stat-value">{{ stats.postCount }}개</div>
          <div class="stat-label">올린 글</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">❤️</div>
          <div class="stat-value">{{ stats.reactionsReceived }}개</div>
          <div class="stat-label">받은 이모지</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">🔖</div>
          <div class="stat-value">{{ stats.savedCount }}개</div>
          <div class="stat-label">저장한 글</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">👍</div>
          <div class="stat-value">{{ stats.reactedCount }}개</div>
          <div class="stat-label">반응한 글</div>
        </div>
      </div>

      <div class="tabs">
        <div class="tab" :class="{ active: activeTab === 'posts' }" @click="setTab('posts')">내가 올린 글</div>
        <div class="tab" :class="{ active: activeTab === 'saved' }" @click="setTab('saved')">저장한 글</div>
        <div class="tab" :class="{ active: activeTab === 'reacted' }" @click="setTab('reacted')">반응한 글</div>
      </div>

      <div class="post-list">
        <div v-for="post in posts" :key="post.id" class="post-row" @click="goToPost(post.id)">
          <div class="post-row-header">
            <div class="post-meta">{{ categoryLabel(post.category) }} · {{ formatRelativeTime(post.createdAt) }}</div>
            <span v-if="activeTab === 'saved'" class="unsave-btn" @click.stop="unsave(post.id)">저장 취소</span>
          </div>
          <div class="post-title">{{ stripSlackMarkdown(post.content).slice(0, 60) }}</div>
          <div class="post-stats">👍 {{ post.reactionCount ?? 0 }} 💬 댓글 {{ post.replyCount ?? 0 }}개</div>
        </div>
        <div v-if="!loading && posts.length === 0" class="empty">아직 게시글이 없습니다.</div>
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <span class="page-btn" @click="prevPage">← 이전</span>
        <span class="page-label">{{ page + 1 }} / {{ totalPages }}</span>
        <span class="page-btn" @click="nextPage">다음 →</span>
      </div>

      <section class="section">
        <div class="section-header">
          <span class="section-title">🏅 이달의 도우미</span>
          <span class="wip-badge">🚧 개발 진행중</span>
        </div>
        <div class="wip-card">곧 만나요 ✨</div>
      </section>
    </template>
  </AppLayout>
</template>

<style scoped>
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6c5ce7, #4a3f8f);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 24px;
}

.avatar-img {
  object-fit: cover;
}

.profile-name {
  font-size: 20px;
  font-weight: 800;
  color: #1a1a2e;
}

.profile-meta {
  font-size: 13.5px;
  color: #636e72;
  margin-top: 2px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
  margin-bottom: 36px;
}

.stat-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
}

.stat-icon {
  font-size: 22px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 20px;
  font-weight: 800;
  color: #1a1a2e;
}

.stat-label {
  font-size: 12.5px;
  color: #636e72;
  margin-top: 2px;
}

.tabs {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
  margin-bottom: 24px;
}

.tab {
  padding: 12px 4px;
  font-size: 15px;
  font-weight: 500;
  color: #636e72;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}

.tab.active {
  font-weight: 700;
  color: #1a1a2e;
  border-bottom-color: #4a3f8f;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}

.post-row {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px 22px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
  cursor: pointer;
}

.post-row:hover {
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.1);
}

.post-row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.post-meta {
  font-size: 12px;
  color: #636e72;
}

.unsave-btn {
  font-size: 12px;
  font-weight: 600;
  color: #e01e5a;
  cursor: pointer;
  white-space: nowrap;
}

.unsave-btn:hover {
  text-decoration: underline;
}

.post-title {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
}

.post-stats {
  font-size: 12.5px;
  color: #636e72;
  margin-top: 8px;
}

.empty {
  text-align: center;
  color: #636e72;
  font-size: 14px;
  padding: 24px 0;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

.page-btn {
  font-size: 13px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 9px;
  cursor: pointer;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.05);
  color: #1a1a2e;
}

.page-label {
  font-size: 12.5px;
  color: #636e72;
}

.section {
  margin-top: 48px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 800;
  color: #1a1a2e;
}

.wip-badge {
  font-size: 11px;
  font-weight: 700;
  color: #636e72;
  background: #efefef;
  padding: 4px 10px;
  border-radius: 6px;
}

.wip-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #636e72;
  font-size: 13.5px;
  opacity: 0.6;
}
</style>
