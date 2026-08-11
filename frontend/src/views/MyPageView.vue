<script setup>
// 마이페이지 - 프로필/통계/내가 올린 글·저장한 글
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import { useAuthStore } from '../stores/auth'
import { useBookmarksStore } from '../stores/bookmarks'
import { useMyPageStore } from '../stores/mypage'
import { removeBookmark } from '../api/bookmarks'
import { formatRelativeTime } from '../utils/relativeTime'
import { stripSlackMarkdown } from '../utils/renderSlackText'
import { CATEGORIES } from '../constants/categories'

// 마이페이지 카테고리 필터에는 상위 6개 카테고리만 노출하고, 하위 태그는 "학습자료"만 보여준다
const FILTER_CATEGORIES = CATEGORIES.map((cat) =>
  cat.value === '학습자료' ? cat : { ...cat, tags: undefined },
)

const router = useRouter()
const authStore = useAuthStore()
const bookmarksStore = useBookmarksStore()
const myPageStore = useMyPageStore()
const { stats, statsError, activeTab, category, tag, posts, page, totalPages, loading, postsError } =
  storeToRefs(myPageStore)

const categoryNavOpen = ref(false)

// 게시글 카드 뱃지용 - 피드(PostCard)와 동일하게 카테고리 아이콘+라벨을 함께 보여준다
function categoryInfo(value) {
  return CATEGORIES.find((cat) => cat.value === value)
}

// 모바일 드롭다운 트리거에 표시할 현재 선택 라벨
const activeFilterLabel = computed(() => {
  if (!category.value) return '전체'
  const cat = CATEGORIES.find((c) => c.value === category.value)
  if (!cat) return '전체'
  if (tag.value) {
    return cat.tags?.find((t) => t.value === tag.value)?.label ?? cat.shortLabel
  }
  return cat.shortLabel
})

// "전체" 항목 옆 개수 - 현재 탭의 통계값을 그대로 사용 (카테고리 개수 합산과 별도 요청 없이 재사용)
const totalForTab = computed(() => {
  if (!stats.value) return 0
  if (activeTab.value === 'saved') return stats.value.savedCount ?? 0
  if (activeTab.value === 'reacted') return stats.value.reactedCount ?? 0
  return stats.value.postCount ?? 0
})

function isActiveCategory(value, tagValue = null) {
  return category.value === value && tag.value === tagValue
}

function selectCategory(value, tagValue = null) {
  myPageStore.setCategory(value, tagValue)
  categoryNavOpen.value = false
}

function goToPost(postId) {
  router.push({ name: 'post-detail', params: { id: postId }, query: { from: 'mypage' } })
}

async function unsave(postId) {
  try {
    await removeBookmark(postId)
    bookmarksStore.setBookmarks(bookmarksStore.bookmarkedPostIds.filter((id) => id !== postId))
    posts.value = posts.value.filter((post) => post.id !== postId)
    if (stats.value) {
      stats.value = { ...stats.value, savedCount: Math.max(0, stats.value.savedCount - 1) }
    }
  } catch {
    postsError.value = '저장 취소에 실패했습니다. 잠시 후 다시 시도해주세요.'
  }
}

function setTab(tab) {
  myPageStore.setTab(tab)
}

function prevPage() {
  myPageStore.prevPage()
}

function nextPage() {
  myPageStore.nextPage()
}

onMounted(() => {
  if (!authStore.isAuthenticated) return
  myPageStore.ensureLoaded()
})
</script>

<template>
  <AppLayout :padding-top="100">
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
      <div v-else-if="statsError" class="empty">{{ statsError }}</div>
      <div v-else class="stats-grid" aria-hidden="true">
        <div class="stat-card" v-for="n in 4" :key="n">
          <div class="stat-icon"><SkeletonBlock width="22px" height="22px" radius="50%" /></div>
          <div class="stat-value"><SkeletonBlock width="50px" height="20px" /></div>
          <div class="stat-label"><SkeletonBlock width="70px" height="12px" /></div>
        </div>
      </div>

      <div class="tabs">
        <div class="tab" :class="{ active: activeTab === 'posts' }" @click="setTab('posts')">내가 올린 글</div>
        <div class="tab" :class="{ active: activeTab === 'saved' }" @click="setTab('saved')">저장한 글</div>
        <div class="tab" :class="{ active: activeTab === 'reacted' }" @click="setTab('reacted')">반응한 글</div>
      </div>

      <div class="tab-body">
        <aside class="category-nav" :class="{ open: categoryNavOpen }">
          <div class="category-nav-trigger" @click="categoryNavOpen = !categoryNavOpen">
            <span>📁 {{ activeFilterLabel }}</span>
            <span class="chevron">▾</span>
          </div>
          <div class="category-nav-list">
            <div class="category-nav-title">📁 카테고리</div>
            <div class="category-nav-item" :class="{ active: !category }" @click="selectCategory(null)">
              <span>전체</span>
              <span class="nav-count">({{ totalForTab }})</span>
            </div>
            <template v-for="cat in FILTER_CATEGORIES" :key="cat.value">
              <div
                class="category-nav-item"
                :class="{ active: isActiveCategory(cat.value) }"
                @click="selectCategory(cat.value)"
              >
                <span>{{ cat.icon }} {{ cat.shortLabel }}</span>
                <span class="nav-count">({{ myPageStore.categoryCount(cat.value) }})</span>
              </div>
              <div
                v-for="sub in cat.tags"
                :key="sub.value"
                class="category-nav-subitem"
                :class="{ active: isActiveCategory(cat.value, sub.value) }"
                @click="selectCategory(cat.value, sub.value)"
              >
                <span>└ {{ sub.label }}</span>
                <span class="nav-count">({{ myPageStore.tagCount(sub.value) }})</span>
              </div>
            </template>
          </div>
        </aside>

        <div class="tab-content">
          <div v-if="loading && posts.length === 0" class="post-list" aria-hidden="true">
            <div class="post-row-skeleton" v-for="n in 4" :key="n">
              <SkeletonBlock width="50%" height="12px" />
              <SkeletonBlock width="80%" height="15px" />
              <SkeletonBlock width="30%" height="12px" />
            </div>
          </div>
          <div v-else class="post-list" :class="{ 'is-loading': loading }">
            <div v-for="post in posts" :key="post.id" class="post-row" @click="goToPost(post.id)">
              <div class="post-row-header">
                <div class="post-badges">
                  <span v-if="categoryInfo(post.category)" class="badge category-badge"
                    >{{ categoryInfo(post.category).icon }} {{ categoryInfo(post.category).shortLabel }}</span
                  >
                  <span v-for="postTag in post.tags" :key="postTag" class="badge tag-badge">🏷️ {{ postTag }}</span>
                </div>
                <span v-if="activeTab === 'saved'" class="unsave-btn" @click.stop="unsave(post.id)"
                  >저장 취소</span
                >
              </div>
              <div class="post-title">{{ stripSlackMarkdown(post.content).slice(0, 60) }}</div>
              <div class="post-stats">
                👍 {{ post.reactionCount ?? 0 }} 💬 댓글 {{ post.replyCount ?? 0 }}개 🔖 저장
                {{ post.bookmarkCount ?? 0 }}개 · {{ formatRelativeTime(post.createdAt) }}
              </div>
            </div>
            <div v-if="postsError" class="empty">{{ postsError }}</div>
            <div v-else-if="posts.length === 0" class="empty">아직 게시글이 없습니다.</div>
          </div>

          <div class="pagination">
            <span class="page-btn" :class="{ disabled: page === 0 }" @click="prevPage">← 이전</span>
            <span class="page-label">{{ page + 1 }} / {{ totalPages || 1 }}</span>
            <span class="page-btn" :class="{ disabled: page + 1 >= totalPages }" @click="nextPage">다음 →</span>
          </div>
        </div>
      </div>

      <section class="section">
        <div class="section-header">
          <span class="section-title">🏅 나의 활동 뱃지</span>
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
  margin-bottom: 4px;
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

.tab-body {
  display: flex;
  border: 1px solid rgba(26, 26, 46, 0.14);
  border-radius: 16px;
}

.category-nav {
  width: 190px;
  flex-shrink: 0;
  padding: 16px 12px;
  border-right: 1px solid rgba(26, 26, 46, 0.08);
}

.category-nav-trigger {
  display: none;
}

.category-nav-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.category-nav-title {
  padding: 4px 12px 8px;
  font-size: 11.5px;
  font-weight: 700;
  color: #8890a3;
  letter-spacing: 0.2px;
}

.category-nav-item,
.category-nav-subitem {
  padding: 7px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #1a1a2e;
  cursor: pointer;
  transition: background 0.15s ease;
}

.category-nav-item {
  font-weight: 580;
}

.category-nav-item:hover:not(.active),
.category-nav-subitem:hover:not(.active) {
  background: rgba(26, 26, 46, 0.05);
}

.category-nav-subitem {
  padding: 3px 14px 4px 30px;
  font-size: 12px;
  color: #636e72;
}

.category-nav-item + .category-nav-subitem {
  padding-top: 1px;
}

.category-nav-item.active,
.category-nav-subitem.active {
  background: #f1eefc;
  color: #4a3f8f;
  font-weight: 700;
}

.nav-count {
  color: #636e72;
  font-size: 11.5px;
  margin-left: 4px;
}

.category-nav-item.active .nav-count,
.category-nav-subitem.active .nav-count {
  color: #4a3f8f;
}

.tab-content {
  flex: 1;
  min-width: 0;
  background: #ffffff;
  border-radius: 0 16px 16px 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

@media (max-width: 768px) {
  .tab-body {
    flex-direction: column;
    border: none;
    border-radius: 0;
  }

  .category-nav {
    width: 100%;
    position: relative;
    padding: 0;
    border-right: none;
  }

  .tab-content {
    border-radius: 16px;
    padding: 16px;
  }

  .category-nav-trigger {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #ffffff;
    border-radius: 12px;
    padding: 12px 16px;
    font-size: 13.5px;
    font-weight: 700;
    color: #1a1a2e;
    box-shadow: 0 2px 8px rgba(26, 26, 46, 0.05);
    cursor: pointer;
    margin-bottom: 16px;
  }

  .chevron {
    color: #636e72;
    transition: transform 0.15s ease;
  }

  .category-nav.open .chevron {
    transform: rotate(180deg);
  }

  .category-nav-list {
    display: none;
  }

  .category-nav.open .category-nav-list {
    display: flex;
    position: absolute;
    top: calc(100% - 10px);
    left: 0;
    right: 0;
    z-index: 20;
    background: #ffffff;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(26, 26, 46, 0.15);
    padding: 8px;
    max-height: 320px;
    overflow-y: auto;
  }
}

.post-list {
  display: flex;
  flex-direction: column;
  /* 4개 미만이어도 4행(124px) 높이를 항상 확보해서, 카테고리 필터로 글 수가 줄어도
     레이아웃 높이가 출렁이지 않게 고정 (행 사이는 gap 대신 구분선으로 구분) */
  min-height: calc(4 * 124px);
}

.post-list.is-loading {
  opacity: 0.5;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.post-row-skeleton {
  padding: 18px 22px;
  height: 124px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.post-row-skeleton:not(:last-child) {
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
}

.post-row {
  padding: 18px 22px;
  height: 124px;
  overflow: hidden;
  cursor: pointer;
  transition: background 0.1s ease;
}

.post-row:not(:last-child) {
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
}

.post-row:hover {
  background: #f1eefc;
}

.post-row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.post-badges {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  overflow: hidden;
  gap: 6px;
}

/* 피드(PostCard.vue)의 뱃지와 동일한 스타일 - 마이페이지도 같은 톤으로 보이게 */
.badge {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
}

.category-badge {
  background: #f1eefc;
  color: #4a3f8f;
}

.tag-badge {
  background: #f4f4f4;
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.post-stats {
  font-size: 12.5px;
  color: #636e72;
  margin-top: 8px;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #636e72;
  font-size: 14px;
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
  font-weight: 700;
  cursor: pointer;
  color: #4a3f8f;
}

.page-btn:hover:not(.disabled) {
  text-decoration: underline;
}

.page-btn.disabled {
  color: #636e72;
  opacity: 0.45;
  cursor: default;
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
