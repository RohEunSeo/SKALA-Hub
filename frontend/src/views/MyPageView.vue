<script setup>
// 마이페이지 - 프로필/통계/내가 올린 글·저장한 글
import { computed, nextTick, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import { useAuthStore } from '../stores/auth'
import { useBookmarksStore } from '../stores/bookmarks'
import { useMyPageStore } from '../stores/mypage'
import { useToastStore } from '../stores/toast'
import { removeBookmark } from '../api/bookmarks'
import { requestGoogleAuthCode } from '../utils/googleAuth'
import { formatRelativeTime } from '../utils/relativeTime'
import { stripSlackMarkdown } from '../utils/renderSlackText'
import { CATEGORIES } from '../constants/categories'

// 마이페이지 카테고리 필터에는 상위 6개 카테고리만 노출하고, 하위 태그는 "학습자료"만 보여준다
const FILTER_CATEGORIES = CATEGORIES.map((cat) =>
  cat.value === '학습자료' ? cat : { ...cat, tags: undefined },
)

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const bookmarksStore = useBookmarksStore()
const myPageStore = useMyPageStore()
const toastStore = useToastStore()
const {
  stats,
  statsError,
  activeTab,
  category,
  tag,
  posts,
  page,
  totalPages,
  loading,
  postsError,
  accountLink,
} = storeToRefs(myPageStore)

const googleLinkLoading = ref(false)

async function handleLinkGoogle() {
  if (googleLinkLoading.value) return
  googleLinkLoading.value = true
  try {
    const code = await requestGoogleAuthCode()
    await myPageStore.linkGoogle(code)
    toastStore.show('구글 계정이 연동되었습니다.')
  } catch (e) {
    if (e.response?.data?.error === 'google_already_linked') {
      toastStore.show('이미 다른 계정에 연동된 구글 계정입니다.')
    } else if (e.message !== 'google_popup_failed') {
      toastStore.show('구글 계정 연동에 실패했습니다. 잠시 후 다시 시도해주세요.')
    }
  } finally {
    googleLinkLoading.value = false
  }
}

async function handleUnlinkGoogle() {
  if (!window.confirm('구글 계정 연동을 해제할까요? 해제 후에는 구글 로그인을 다시 사용할 수 없습니다.')) return
  try {
    await myPageStore.unlinkGoogle()
    toastStore.show('구글 계정 연동이 해제되었습니다.')
  } catch {
    toastStore.show('연동 해제에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}

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
    myPageStore.adjustSavedCount(-1)
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

// 저장하기 토스트의 "저장한 글 보기"로 들어왔을 때 - ?tab=saved 로 해당 탭을 열고
// ?highlight=<postId>로 그 게시글을 스크롤+하이라이트
const VALID_TABS = ['posts', 'saved', 'reacted']
const highlightedPostId = ref(null)

onMounted(async () => {
  if (!authStore.isAuthenticated) return
  const queryTab = VALID_TABS.includes(route.query.tab) ? route.query.tab : null
  if (queryTab && myPageStore.loaded && activeTab.value !== queryTab) {
    await myPageStore.setTab(queryTab)
  } else {
    if (queryTab) activeTab.value = queryTab
    await myPageStore.ensureLoaded()
  }

  const highlightId = route.query.highlight ? Number(route.query.highlight) : null
  if (!highlightId) return
  highlightedPostId.value = highlightId
  await nextTick()
  document
    .querySelector(`[data-post-id="${highlightId}"]`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  setTimeout(() => {
    if (highlightedPostId.value === highlightId) highlightedPostId.value = null
  }, 2500)
})
</script>

<template>
  <AppLayout :padding-top="100">
    <AuthRequired v-if="!authStore.isAuthenticated" message="마이페이지를 보려면 SKALA 교육생 인증이 필요합니다" />
    <template v-else>
      <div class="profile-header">
        <div class="profile-info">
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

        <div v-if="authStore.effectiveIsAdmin" class="google-banner">
          <button v-if="accountLink.googleLinked" class="google-link-btn google-link-btn-done" disabled>
            ✅ 연동 완료됨
          </button>
          <button v-else class="google-link-btn" :disabled="googleLinkLoading" @click="handleLinkGoogle">
            <svg class="google-icon" viewBox="0 0 18 18" width="16" height="16" aria-hidden="true">
              <path fill="#4285F4" d="M17.64 9.2045c0-.6381-.0573-1.2518-.1636-1.8409H9v3.4814h4.8436c-.2086 1.125-.8427 2.0782-1.7959 2.7164v2.2581h2.9087c1.7018-1.5668 2.6836-3.8741 2.6836-6.615z" />
              <path fill="#34A853" d="M9 18c2.43 0 4.4673-.806 5.9564-2.1805l-2.9087-2.2581c-.8059.54-1.8368.8586-3.0477.8586-2.3436 0-4.3282-1.5831-5.0359-3.7104H.9573v2.3318C2.4382 15.9832 5.4818 18 9 18z" />
              <path fill="#FBBC05" d="M3.9641 10.71c-.18-.54-.2822-1.1168-.2822-1.71s.1023-1.17.2822-1.71V4.9582H.9573C.3477 6.1732 0 7.5477 0 9s.3477 2.8268.9573 4.0418L3.9641 10.71z" />
              <path fill="#EA4335" d="M9 3.5795c1.3214 0 2.5077.4541 3.4405 1.346l2.5813-2.5814C13.4632.8918 11.426 0 9 0 5.4818 0 2.4382 2.0168.9573 4.9582L3.9641 7.29C4.6718 5.1627 6.6564 3.5795 9 3.5795z" />
            </svg>
            구글 계정 연동하기
          </button>

          <div class="google-banner-text">
            <template v-if="accountLink.googleLinked">
              <div class="google-banner-desc">
                * {{ accountLink.googleEmail }}로 연동되어 있습니다.
                <span v-if="authStore.effectiveIsAdmin" class="google-unlink-btn" @click="handleUnlinkGoogle">연동 해제</span>
              </div>
            </template>
            <template v-else>
              <div class="google-banner-desc">
                * 교육 수료 후 Slack 계정이 비활성화되어 슬랙 로그인이 불가합니다. 구글
                계정을 연동하면 이후에도 동일 계정으로 접근 가능합니다.
              </div>
              <div class="google-banner-warning">
                ⚠️ 계정이 비활성화되기 전에 반드시 연동해야 수료 후에도 접근할 수 있습니다.
              </div>
            </template>
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
            <div
              v-for="post in posts"
              :key="post.id"
              class="post-row"
              :class="{ 'post-row-highlighted': highlightedPostId === post.id }"
              :data-post-id="post.id"
              @click="goToPost(post.id)"
            >
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
    </template>
  </AppLayout>
</template>

<style scoped>
.profile-header {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 12px;
  margin-bottom: 32px;
}

.profile-info {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.google-banner {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  background: transparent;
  padding: 10px 12px;
  flex-wrap: nowrap;
  flex: 1;
  min-width: 0;
}

.google-banner-text {
  min-width: 0;
  margin-bottom: 2px;
  text-align: left;
}

.google-banner-desc {
  font-size: 12.5px;
  color: #636e72;
  line-height: 1.5;
}

.google-banner-warning {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 700;
  color: #e01e5a;
}

.google-unlink-btn {
  font-size: 12px;
  font-weight: 600;
  color: #e01e5a;
  cursor: pointer;
  white-space: nowrap;
}

.google-unlink-btn:hover {
  text-decoration: underline;
}

.google-link-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 18px;
  background: #ffffff;
  color: #1a1a2e;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  box-shadow: 0 4px 14px rgba(26, 26, 46, 0.12);
}

.google-link-btn:hover {
  background: #f4f4f4;
}

.google-link-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.google-link-btn-done {
  background: #e7f8ee;
  color: #1a8f4c;
  opacity: 1;
}

.google-icon {
  flex-shrink: 0;
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
  .profile-header {
    flex-wrap: wrap;
  }

  .google-banner {
    flex-wrap: wrap;
    flex: 1 1 100%;
    margin-left: 0;
  }

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

.post-row-highlighted {
  background: #f1eefc;
  box-shadow: inset 0 0 0 2px #4a3f8f;
  transition: background 0.2s ease, box-shadow 0.2s ease;
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

</style>
