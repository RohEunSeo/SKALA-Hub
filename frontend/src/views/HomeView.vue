<script setup>
// 메인 홈 화면 - 요약 통계 + 카테고리별 아카이브 (별도 로그인 페이지 없이 여기서 슬랙 OAuth 콜백도 받음)
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import SkeletonBlock from '../components/SkeletonBlock.vue'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { fetchHomeSummary, fetchHomeLeaderboard } from '../api/home'
import { getSlackLoginUrl } from '../api/auth'
import { formatRelativeTime } from '../utils/relativeTime'
import { stripSlackMarkdown } from '../utils/renderSlackText'
import { CATEGORIES } from '../constants/categories'

const AUTO_SLIDE_MS = 4000

const BOARDS = [
  { key: 'topReactions', label: '🔥 가장 반응이 많은 글', unit: '👍🏻' },
  { key: 'topComments', label: '💬 가장 댓글이 많은 글', unit: '💬' },
  { key: 'topSaves', label: '🔖 가장 많이 저장된 글', unit: '🔖' },
]

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()

const summary = ref(null)
const summaryError = ref('')
const leaderboard = ref(null)
const leaderboardLoading = ref(true)
const leaderboardError = ref('')
const leaderboardPeriod = ref('all')
const boardIndex = ref(0)
let autoTimer = null

async function loadSummary() {
  summaryError.value = ''
  try {
    const { data } = await fetchHomeSummary()
    summary.value = data
  } catch {
    summaryError.value = '요약 정보를 불러오지 못했습니다.'
  }
}

async function loadLeaderboard() {
  leaderboardLoading.value = true
  leaderboardError.value = ''
  try {
    const { data } = await fetchHomeLeaderboard(leaderboardPeriod.value)
    leaderboard.value = data
  } catch {
    leaderboardError.value = '순위보드를 불러오지 못했습니다.'
  } finally {
    leaderboardLoading.value = false
  }
}

function selectPeriod(period) {
  if (leaderboardPeriod.value === period) return
  leaderboardPeriod.value = period
  boardIndex.value = 0
  loadLeaderboard()
}

// leaderboard[board.key]를 템플릿에서 직접 접근하지 않고 항상 배열을 보장해서 반환
function boardEntries(key) {
  return leaderboard.value?.[key] ?? []
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
  return summary.value?.categoryCounts?.find((item) => item.category === value)?.count ?? 0
}

function goToCategory(value) {
  postsStore.setCategory(value, null)
  router.push({ name: 'feed' })
}

// 카테고리 원색(cat.color)을 그대로 쓰면 너무 쨍해서, 흰색/검은색을 섞어 연한 파스텔 톤으로 가공
function hexToRgb(hex) {
  const value = hex.replace('#', '')
  return [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16))
}

function mixWith(hex, target, amount, alpha = 1) {
  const [r, g, b] = hexToRgb(hex)
  const mix = (c) => Math.round(c + (target - c) * amount)
  return alpha === 1 ? `rgb(${mix(r)}, ${mix(g)}, ${mix(b)})` : `rgba(${mix(r)}, ${mix(g)}, ${mix(b)}, ${alpha})`
}

// 본체는 원색에 흰색을 섞어 연하게, 탭은 본체보다 더 밝게 - 맥 Finder 폴더처럼 탭/본체 톤 차이로 입체감
function folderBodyColor(color) {
  return mixWith(color, 255, 0.4)
}

function folderTabColor(color) {
  return mixWith(color, 255, 0.68)
}

function folderTextColor(color) {
  return mixWith(color, 0, 0.42)
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

    <p class="eyebrow">SKALA 판교캠퍼스 교육생을 위한 정보공유 아카이빙 서비스</p>
    <h1 class="greeting">
      <template v-if="authStore.isAuthenticated">안녕하세요, {{ authStore.user?.name }}님 👋</template>
      <template v-else>SKALA Hub에 오신 것을 환영합니다 👋</template>
    </h1>
    <span class="slogan">슬랙 정보공유 채널, 카테고리별로 찾고 저장하세요 🔖</span>

    <div v-if="summary" class="stat-pills">
      <div v-if="authStore.isAuthenticated && authStore.user?.cohort" class="pill">
        🎓 SKALA {{ authStore.user.cohort }} <strong>{{ summary.cohortDay }}일째</strong>
      </div>
      <div class="pill">📝 전체 게시글 <strong>{{ summary.totalPostCount ?? 0 }}개</strong></div>
      <div class="pill">👥 함께하는 교육생 <strong>{{ summary.userCount ?? 0 }}명</strong></div>
      <div class="pill">📬 오늘 새 글 <strong>{{ summary.todayNewPostCount ?? 0 }}개</strong></div>
      <div class="pill muted">🕐 마지막 동기화: {{ formatRelativeTime(summary.lastSyncedAt) }}</div>
    </div>
    <div v-else-if="summaryError" class="status-message">{{ summaryError }}</div>
    <div v-else class="stat-pills" aria-hidden="true">
      <div class="pill" v-for="n in 4" :key="n"><SkeletonBlock width="120px" /></div>
    </div>

    <section class="section">
      <div class="section-header">
        <span class="section-title">🏆 순위보드</span>
      </div>
      <div class="leaderboard-card">
        <div class="period-tabs">
          <span class="period-tab" :class="{ active: leaderboardPeriod === 'all' }" @click="selectPeriod('all')">전체</span>
          <span class="period-tab" :class="{ active: leaderboardPeriod === 'today' }" @click="selectPeriod('today')">오늘</span>
          <span class="period-tab" :class="{ active: leaderboardPeriod === 'week' }" @click="selectPeriod('week')">이번주</span>
        </div>
        <div class="leaderboard-viewport">
          <div class="leaderboard-track" :style="{ transform: `translateX(-${boardIndex * 100}%)` }">
            <div v-for="board in BOARDS" :key="board.key" class="leaderboard-board">
              <div class="board-title">{{ board.label }}</div>
              <div v-if="leaderboardLoading" class="board-empty">불러오는 중...</div>
              <div v-else-if="leaderboardError" class="board-empty">{{ leaderboardError }}</div>
              <div v-else class="board-list">
                <div
                  v-for="(entry, idx) in boardEntries(board.key)"
                  :key="entry.post.id"
                  class="board-row"
                  @click="goToPost(entry.post.id)"
                >
                  <span class="board-rank">{{ idx + 1 }}</span>
                  <span class="board-post-title">{{ previewText(entry.post.content) }}</span>
                  <span class="board-count">{{ entry.count }} {{ board.unit }}</span>
                </div>
                <div v-if="boardEntries(board.key).length === 0" class="board-empty">아직 데이터가 없습니다</div>
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
          :style="{ background: folderBodyColor(cat.color), '--tab-color': folderTabColor(cat.color) }"
          @click="goToCategory(cat.value)"
        >
          <div class="category-icon">{{ cat.icon }}</div>
          <div>
            <div class="category-label" :style="{ color: folderTextColor(cat.color) }">{{ cat.label }}</div>
            <div class="category-count" :style="{ color: folderTextColor(cat.color) }">글 {{ categoryCount(cat.value) }}개</div>
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
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 14px;
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

.eyebrow {
  display: block;
  font-size: 11px;
  font-weight: 400;
  color: #636e72;
  text-transform: uppercase;
  letter-spacing: 0.01em;
}

.greeting {
  margin-top: 0px;
  font-size: 24px;
  font-weight: 800;
  color: #1a1a2e;
}

.slogan {
  display: block;
  margin-top: 5px;
  font-size: 14.5px;
  color: #4a3f8f;
  font-weight: bold;
}

.stat-pills {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.status-message {
  margin-top: 20px;
  color: #636e72;
  font-size: 13px;
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

.period-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
}

.period-tab {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12.5px;
  font-weight: 700;
  color: #636e72;
  background: #f4f4f4;
  cursor: pointer;
}

.period-tab.active {
  background: #4a3f8f;
  color: #ffffff;
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
  margin-top: 16px;
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

/* 기본(전체화면)은 1행 6열, 화면이 좁아지면 3열 → 2열 → 1열로 반응형 전환 */
.category-grid {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 18px;
}

@media (max-width: 1100px) {
  .category-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 24px;
  }
}

@media (max-width: 860px) {
  .category-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .category-grid {
    gap: 12px;
  }
}

/* 맥 Finder 폴더 아이콘 느낌 - 둥근 본체(진한 톤) + 왼쪽 위로 삐져나온 탭(연한 톤), 테두리선 없이 그림자로만 입체감 */
.category-card {
  position: relative;
  min-width: 0;
  margin-top: 14px;
  cursor: pointer;
  border-radius: 13px;
  padding: 20px 18px 18px;
  aspect-ratio: 4 / 3;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 10px 20px rgba(26, 26, 46, 0.15), inset 0 -14px 12px -10px rgba(0, 0, 0, 0.12);
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.category-card:hover {
  transform: translateY(-6px) scale(1.04);
  box-shadow: 0 18px 28px rgba(26, 26, 46, 0.22), inset 0 -14px 12px -10px rgba(0, 0, 0, 0.12);
}

.category-card::before {
  content: '';
  position: absolute;
  top: -10px;
  left: 0;
  width: 46%;
  height: 22px;
  border-radius: 10px 10px 0 0;
  background: var(--tab-color);
  transition: transform 0.22s ease;
  transform-origin: bottom left;
}

/* 마우스 올리면 위쪽 탭이 살짝 들려서 폴더가 열리는 듯한 느낌 */
.category-card:hover::before {
  transform: translateY(-3px) rotate(-3deg);
}

.category-icon {
  font-size: 23px;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.08));
  transition: transform 0.22s ease;
}

.category-card:hover .category-icon {
  transform: scale(1.1);
}

.category-label {
  font-weight: 700;
  font-size: 14px;
}

.category-count {
  font-size: 13.3px;
  margin-top: 1px;
  opacity: 0.75;
}
</style>
