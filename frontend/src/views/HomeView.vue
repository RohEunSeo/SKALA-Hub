<script setup>
// 메인 홈 화면 - 요약 통계 + 카테고리별 아카이브 (별도 로그인 페이지 없이 여기서 슬랙 OAuth 콜백도 받음)
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { fetchHomeSummary, fetchHomeLeaderboard } from '../api/home'
import { getSlackLoginUrl } from '../api/auth'
import { formatRelativeTime } from '../utils/relativeTime'
import { stripSlackMarkdown } from '../utils/renderSlackText'
import { CATEGORIES } from '../constants/categories'

const AUTO_SLIDE_MS = 2000

const BOARDS = [
  { key: 'topReactions', label: '🔥 가장 반응이 많은 글', unit: '반응' },
  { key: 'topComments', label: '💬 가장 댓글이 많은 글', unit: '댓글' },
  { key: 'topSaves', label: '🔖 가장 많이 저장된 글', unit: '저장' },
]

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()

const summary = ref(null)
const leaderboard = ref(null)
const boardIndex = ref(0)
let autoTimer = null

async function loadSummary() {
  const { data } = await fetchHomeSummary()
  summary.value = data
}

async function loadLeaderboard() {
  const { data } = await fetchHomeLeaderboard()
  leaderboard.value = data
}

function startAutoSlide() {
  clearInterval(autoTimer)
  autoTimer = setInterval(() => {
    boardIndex.value = (boardIndex.value + 1) % BOARDS.length
  }, AUTO_SLIDE_MS)
}

function prevBoard() {
  boardIndex.value = (boardIndex.value - 1 + BOARDS.length) % BOARDS.length
  startAutoSlide()
}

function nextBoard() {
  boardIndex.value = (boardIndex.value + 1) % BOARDS.length
  startAutoSlide()
}

function goToBoard(index) {
  boardIndex.value = index
  startAutoSlide()
}

function goToPost(id) {
  router.push({ name: 'post-detail', params: { id } })
}

function previewText(content) {
  return stripSlackMarkdown(content).slice(0, 36)
}

function handleLogin() {
  window.location.href = getSlackLoginUrl()
}

function categoryCount(value) {
  return summary.value?.categoryCounts.find((item) => item.category === value)?.count ?? 0
}

function goToCategory(value) {
  postsStore.setCategory(value, null)
  router.push({ name: 'feed' })
}

onMounted(() => {
  // 슬랙 OAuth 콜백이 홈으로 바로 돌아오므로 여기서 토큰을 받는다 (별도 로그인 페이지 없음)
  const { token, error } = route.query
  if (token) {
    authStore.setAuth(token)
    router.replace({ name: 'home' })
  } else if (error) {
    console.error('슬랙 로그인 실패:', error)
  }

  loadSummary()
  loadLeaderboard()
  startAutoSlide()
})

onUnmounted(() => {
  clearInterval(autoTimer)
})
</script>

<template>
  <AppLayout :max-width="1040">
    <div v-if="!authStore.isAuthenticated" class="login-banner">
      <span>👋 SKALA 교육생이신가요? 로그인하면 피드·마이페이지를 모두 이용할 수 있어요.</span>
      <button class="login-btn" @click="handleLogin">Slack으로 로그인</button>
    </div>

    <div v-if="authStore.isAuthenticated" class="top-bar">
      <img
        v-if="authStore.user?.profileImg"
        class="avatar avatar-img"
        :src="authStore.user.profileImg"
        :alt="authStore.user.name"
        title="마이페이지로 이동"
        @click="router.push({ name: 'mypage' })"
      />
      <div v-else class="avatar" title="마이페이지로 이동" @click="router.push({ name: 'mypage' })">
        {{ authStore.user?.name?.charAt(0) }}
      </div>
    </div>

    <h1 class="greeting">
      <template v-if="authStore.isAuthenticated">안녕하세요, {{ authStore.user?.name }}님 👋</template>
      <template v-else>SKALA Hub에 오신 것을 환영합니다 👋</template>
    </h1>
    <p class="subtitle">SKALA 부트캠프 교육생을 위한 정보공유 아카이빙 서비스입니다</p>

    <div v-if="summary" class="stat-pills">
      <div v-if="authStore.isAuthenticated" class="pill">
        🎓 SKALA {{ authStore.user?.cohort }} <strong>{{ summary.cohortDay }}일째</strong>
      </div>
      <div class="pill">📝 전체 게시글 <strong>{{ summary.totalPostCount }}개</strong></div>
      <div class="pill">📬 오늘 새 글 <strong>{{ summary.todayNewPostCount }}개</strong></div>
      <div class="pill muted">🕐 마지막 동기화: {{ formatRelativeTime(summary.lastSyncedAt) }}</div>
    </div>

    <section class="section">
      <div class="section-header">
        <span class="section-title">🏆 순위보드</span>
      </div>
      <div class="leaderboard-card">
        <div class="leaderboard-viewport">
          <div class="leaderboard-track" :style="{ transform: `translateX(-${boardIndex * 100}%)` }">
            <div v-for="board in BOARDS" :key="board.key" class="leaderboard-board">
              <div class="board-title">{{ board.label }}</div>
              <div v-if="leaderboard" class="board-list">
                <div
                  v-for="(entry, idx) in leaderboard[board.key]"
                  :key="entry.post.id"
                  class="board-row"
                  @click="goToPost(entry.post.id)"
                >
                  <span class="board-rank">{{ idx + 1 }}</span>
                  <span class="board-post-title">{{ previewText(entry.post.content) }}</span>
                  <span class="board-count">{{ entry.count }}{{ board.unit }}</span>
                </div>
                <div v-if="leaderboard[board.key].length === 0" class="board-empty">아직 데이터가 없습니다</div>
              </div>
            </div>
          </div>
        </div>
        <div class="leaderboard-nav">
          <span class="nav-btn" @click="prevBoard">‹ 이전</span>
          <div class="nav-dots">
            <span
              v-for="(board, index) in BOARDS"
              :key="board.key"
              class="dot"
              :class="{ active: index === boardIndex }"
              @click="goToBoard(index)"
            ></span>
          </div>
          <span class="nav-btn" @click="nextBoard">다음 ›</span>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="section-title">카테고리별 아카이브</div>
      <div class="category-grid">
        <div
          v-for="cat in CATEGORIES"
          :key="cat.value"
          class="category-card"
          :style="{ background: cat.color }"
          @click="goToCategory(cat.value)"
        >
          <div class="category-icon">{{ cat.icon }}</div>
          <div>
            <div class="category-label">{{ cat.label }}</div>
            <div class="category-count">글 {{ categoryCount(cat.value) }}개</div>
          </div>
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.login-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: #f1eefc;
  border: 1px solid rgba(74, 63, 143, 0.15);
  border-radius: 14px;
  padding: 14px 20px;
  margin-bottom: 24px;
  font-size: 13.5px;
  color: #4a3f8f;
  font-weight: 600;
}

.login-btn {
  flex-shrink: 0;
  padding: 9px 18px;
  background: #4a3f8f;
  color: #fff;
  border: none;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.login-btn:hover {
  background: #6c5ce7;
}

.top-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6c5ce7, #4a3f8f);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}

.avatar-img {
  object-fit: cover;
}

.greeting {
  font-size: 24px;
  font-weight: 800;
  color: #1a1a2e;
}

.subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: #636e72;
}

.stat-pills {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.pill {
  background: #ffffff;
  border-radius: 10px;
  padding: 9px 16px;
  font-size: 13px;
  color: #1a1a2e;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.05);
}

.pill strong {
  color: #4a3f8f;
}

.pill.muted {
  color: #636e72;
}

.section {
  margin-top: 40px;
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

.leaderboard-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
}

.leaderboard-viewport {
  overflow: hidden;
}

.leaderboard-track {
  display: flex;
  transition: transform 0.4s ease;
}

.leaderboard-board {
  flex: 0 0 100%;
  min-width: 0;
}

.board-title {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 10px;
}

.board-list {
  min-height: 132px;
}

.board-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid rgba(26, 26, 46, 0.06);
  cursor: pointer;
}

.board-row:last-child {
  border-bottom: none;
}

.board-row:hover .board-post-title {
  color: #4a3f8f;
  text-decoration: underline;
}

.board-rank {
  font-size: 14px;
  font-weight: 800;
  color: #4a3f8f;
  width: 18px;
  flex-shrink: 0;
}

.board-post-title {
  font-size: 13.5px;
  color: #1a1a2e;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-count {
  font-size: 12px;
  font-weight: 600;
  color: #636e72;
  flex-shrink: 0;
}

.board-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #636e72;
}

.leaderboard-nav {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-btn {
  font-size: 12.5px;
  font-weight: 600;
  color: #636e72;
  cursor: pointer;
}

.nav-btn:hover {
  color: #4a3f8f;
}

.nav-dots {
  display: flex;
  gap: 6px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(26, 26, 46, 0.15);
  cursor: pointer;
}

.dot.active {
  background: #4a3f8f;
}

.category-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 22px 16px;
}

/* 맥 Finder 폴더 느낌 - 위에 작은 탭 + 아래 본체, 본체보다 어두운 탭 색으로 입체감 */
.category-card {
  position: relative;
  margin-top: 10px;
  cursor: pointer;
  border-radius: 3px 14px 14px 14px;
  padding: 20px 18px 16px;
  min-height: 92px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 8px 16px rgba(26, 26, 46, 0.14);
}

.category-card::before {
  content: '';
  position: absolute;
  top: -9px;
  left: 0;
  width: 42%;
  height: 15px;
  border-radius: 6px 8px 0 0;
  background: inherit;
  filter: brightness(0.85);
}

.category-icon {
  font-size: 20px;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.1));
}

.category-label {
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.category-count {
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  margin-top: 2px;
}
</style>
