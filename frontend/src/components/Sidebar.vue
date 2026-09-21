<script setup>
// 좌측 네비게이션 사이드바 - 로고/메뉴/카테고리/유저 프로필
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { useMyPageStore } from '../stores/mypage'
import { useUiStore } from '../stores/ui'
import { CATEGORIES } from '../constants/categories'
import InquiryModal from './InquiryModal.vue'
import skLogo from '../assets/sk_logo.png'

const emit = defineEmits(['navigate', 'collapse'])

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()
const myPageStore = useMyPageStore()
const uiStore = useUiStore()
const showInquiryModal = ref(false)

onMounted(() => {
  postsStore.loadCategoryCounts()
})

// 마이페이지 바로가기 - 탭 값은 MyPageView의 ?tab= 쿼리/activeTab과 동일
const MY_TABS = [
  { tab: 'posts', icon: '📝', label: '내가 올린 글', countKey: 'postCount' },
  { tab: 'saved', icon: '🔖', label: '저장한 글', countKey: 'savedCount' },
  { tab: 'reacted', icon: '👍', label: '반응한 글', countKey: 'reactedCount' },
]

// 로그인된 상태에서만 개수 조회 (로그인 직후에도 반영되도록 immediate watch)
watch(
  () => authStore.isAuthenticated,
  (loggedIn) => {
    if (loggedIn) myPageStore.ensureStats()
  },
  { immediate: true },
)

function myTabCount(countKey) {
  return myPageStore.stats?.[countKey]
}

function isActiveMyTab(tab) {
  return route.name === 'mypage' && myPageStore.activeTab === tab
}

function goMyPageTab(tab) {
  window.scrollTo({ top: 0, behavior: 'auto' })
  if (route.name === 'mypage') {
    // 이미 마이페이지면 라우트 변경이 없어 MyPageView가 재마운트되지 않으므로 스토어로 탭만 전환
    myPageStore.setTab(tab)
    return
  }
  router.push({ name: 'mypage', query: { tab } })
}

// 피드 하위 메뉴 - FeedView 상단 탭(링크 모음 / SKALA 커리큘럼)으로 바로 진입. 활성 표시는 FeedView가
// 스토어(uiStore.feedTab)에 동기화해 둔 현재 탭 기준
const FEED_TABS = [
  { tab: 'links', icon: '🔗', label: '링크 모음' },
  { tab: 'curriculum', logo: skLogo, label: 'SKALA 커리큘럼' },
]

function isActiveFeedTab(tab) {
  return route.name === 'feed' && uiStore.feedTab === tab
}

function goFeedTab(tab) {
  window.scrollTo({ top: 0, behavior: 'auto' })
  if (route.name === 'feed') {
    // 이미 피드 화면이면 재마운트되지 않으므로 스토어 값만 바꿔 FeedView가 탭을 전환하게 함
    uiStore.feedTab = tab
    return
  }
  // 다른 화면에서는 홈의 "링크 모음" 바로가기와 동일하게 링크 탭 상태를 미리 켜고 쿼리로 진입
  if (tab === 'links') postsStore.setHasLink(true)
  router.push({ name: 'feed', query: { tab } })
}

const userInitial = computed(() => authStore.user?.name?.charAt(0) ?? '?')
const userMeta = computed(() => {
  const user = authStore.user
  if (!user) return ''
  return [user.cohort, user.campus, user.classNum].filter(Boolean).join(' ')
})

function isActiveCategory(value, tagValue = null) {
  // 피드 화면을 벗어나면(홈/마이페이지 등) 보라색 활성 표시를 꺼서, 선택된 카테고리 상태는 유지하되 다른 화면에 잘못 남지 않게 함
  return route.name === 'feed' && postsStore.category === value && postsStore.tag === tagValue
}

function selectCategory(value, tagValue = null) {
  // 링크 모음 탭에 있어도 사이드바 카테고리를 누르면 항상 게시글 탭으로 전환 (직접 대입, setHasLink 아님 - setCategory가 어차피 다시 조회하므로 중복 조회 방지)
  postsStore.hasLink = null
  postsStore.setCategory(value, tagValue)
  window.scrollTo({ top: 0, behavior: 'auto' })
  if (route.name !== 'feed') {
    router.push({ name: 'feed' })
  }
}

// 하위 메뉴가 있는 항목(학습 자료/교육생 서비스/기타 카테고리 + 피드/마이페이지 메뉴) 접기/펼치기 - 기본은 전부 펼침.
// 카테고리는 value를 그대로, 피드/마이페이지는 카테고리 value와 겹치지 않게 'nav:' 접두어 키로 같은 목록에 저장
// 사용자가 직접 접은 상태는 새로고침해도 유지되도록 localStorage에 저장 (접근 불가 환경이면 기본값 펼침)
const COLLAPSED_KEY = 'skala_hub_sidebar_collapsed'

function loadCollapsed() {
  try {
    const parsed = JSON.parse(localStorage.getItem(COLLAPSED_KEY))
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const NAV_FEED_KEY = 'nav:feed'
const NAV_MYPAGE_KEY = 'nav:mypage'
const collapsedCategories = ref(loadCollapsed())

function isCollapsed(value) {
  return collapsedCategories.value.includes(value)
}

function toggleCategory(value) {
  collapsedCategories.value = isCollapsed(value)
    ? collapsedCategories.value.filter((v) => v !== value)
    : [...collapsedCategories.value, value]
  try {
    localStorage.setItem(COLLAPSED_KEY, JSON.stringify(collapsedCategories.value))
  } catch {
    // 저장 실패해도 이번 방문 동안은 정상 동작
  }
}

// 접힌 카테고리 안의 하위 태그가 선택돼 있으면 상위 행을 연하게 강조 (선택된 곳이 숨겨져 있음을 알려줌)
function hasHiddenActiveTag(value) {
  return isCollapsed(value) && route.name === 'feed' && postsStore.category === value && postsStore.tag != null
}

// 공지 딥링크 등으로 접힌 카테고리의 태그가 선택되면 자동으로 펼침 (저장된 접힘 상태는 건드리지 않음)
watch(
  () => [postsStore.category, postsStore.tag],
  ([category, tag]) => {
    if (tag != null && isCollapsed(category)) {
      collapsedCategories.value = collapsedCategories.value.filter((v) => v !== category)
    }
  },
  { immediate: true },
)

function handleLogout() {
  authStore.clearAuth()
  router.push({ name: 'home' })
}
</script>

<template>
  <aside class="sidebar" @click="emit('navigate')">
    <div>
      <div class="logo-row">
        <RouterLink to="/" class="logo">
          <span class="logo-text">SKALA<span class="logo-accent">Hub</span></span>
        </RouterLink>
        <button
          class="collapse-btn"
          aria-label="사이드바 접기"
          @click.stop="emit('collapse')"
        >
          «
        </button>
      </div>

      <nav class="nav">
        <RouterLink to="/" class="nav-item" :class="{ active: route.name === 'home' }">🏠 홈</RouterLink>
        <RouterLink
          to="/feed"
          class="nav-item"
          :class="{ active: route.name === 'feed', 'has-toggle': authStore.isAuthenticated }"
        >
          <span>📋 피드</span>
          <button
            v-if="authStore.isAuthenticated"
            class="category-toggle"
            :class="{ collapsed: isCollapsed(NAV_FEED_KEY) }"
            :aria-expanded="!isCollapsed(NAV_FEED_KEY)"
            :aria-label="`피드 하위 메뉴 ${isCollapsed(NAV_FEED_KEY) ? '펼치기' : '접기'}`"
            @click.prevent.stop="toggleCategory(NAV_FEED_KEY)"
          >
            <svg viewBox="0 0 12 12" aria-hidden="true">
              <path d="M3 4.5 6 7.5 9 4.5" />
            </svg>
          </button>
        </RouterLink>
        <div v-if="authStore.isAuthenticated" class="category-subs" :class="{ collapsed: isCollapsed(NAV_FEED_KEY) }">
          <div class="category-subs-inner">
            <div
              v-for="item in FEED_TABS"
              :key="item.tab"
              class="category-subitem"
              :class="{ active: isActiveFeedTab(item.tab) }"
              @click="goFeedTab(item.tab)"
            >
              └
              <img v-if="item.logo" :src="item.logo" class="subitem-logo" alt="" />
              <template v-else>{{ item.icon }}</template>
              {{ item.label }}
            </div>
          </div>
        </div>
        <RouterLink
          to="/mypage"
          class="nav-item"
          :class="{ active: route.name === 'mypage', 'has-toggle': authStore.isAuthenticated }"
        >
          <span>👤 마이페이지</span>
          <button
            v-if="authStore.isAuthenticated"
            class="category-toggle"
            :class="{ collapsed: isCollapsed(NAV_MYPAGE_KEY) }"
            :aria-expanded="!isCollapsed(NAV_MYPAGE_KEY)"
            :aria-label="`마이페이지 하위 메뉴 ${isCollapsed(NAV_MYPAGE_KEY) ? '펼치기' : '접기'}`"
            @click.prevent.stop="toggleCategory(NAV_MYPAGE_KEY)"
          >
            <svg viewBox="0 0 12 12" aria-hidden="true">
              <path d="M3 4.5 6 7.5 9 4.5" />
            </svg>
          </button>
        </RouterLink>
        <div v-if="authStore.isAuthenticated" class="category-subs" :class="{ collapsed: isCollapsed(NAV_MYPAGE_KEY) }">
          <div class="category-subs-inner">
            <div
              v-for="item in MY_TABS"
              :key="item.tab"
              class="category-subitem"
              :class="{ active: isActiveMyTab(item.tab) }"
              @click="goMyPageTab(item.tab)"
            >
              └ {{ item.icon }} {{ item.label }}
              <span v-if="myTabCount(item.countKey) != null" class="category-count"
                >({{ myTabCount(item.countKey) }})</span
              >
            </div>
          </div>
        </div>
        <RouterLink to="/dashboard" class="nav-item" :class="{ active: route.name === 'dashboard' }"
          >🌱 대시보드</RouterLink
        >
        <RouterLink
          v-if="authStore.effectiveIsAdmin"
          to="/admin"
          class="nav-item admin-nav-item"
          :class="{ active: route.name === 'admin' }"
          >🛡️ 관리자 모드</RouterLink
        >
      </nav>

      <div class="divider"></div>

      <div class="category-list">
        <template v-for="cat in CATEGORIES" :key="cat.value">
          <div
            class="category-item"
            :class="{ active: isActiveCategory(cat.value), 'has-hidden-active': hasHiddenActiveTag(cat.value) }"
            @click="selectCategory(cat.value)"
          >
            <span>
              {{ cat.icon }} {{ cat.label }}
              <span class="category-count">({{ postsStore.categoryCount(cat.value) }})</span>
            </span>
            <!-- 하위 태그가 있는 카테고리만 오른쪽 끝에 접기/펼치기 화살표 (클릭해도 카테고리 필터는 바뀌지 않음) -->
            <button
              v-if="cat.tags?.length"
              class="category-toggle"
              :class="{ collapsed: isCollapsed(cat.value) }"
              :aria-expanded="!isCollapsed(cat.value)"
              :aria-label="`${cat.label} 하위 카테고리 ${isCollapsed(cat.value) ? '펼치기' : '접기'}`"
              @click.stop="toggleCategory(cat.value)"
            >
              <svg viewBox="0 0 12 12" aria-hidden="true">
                <path d="M3 4.5 6 7.5 9 4.5" />
              </svg>
            </button>
          </div>
          <div v-if="cat.tags?.length" class="category-subs" :class="{ collapsed: isCollapsed(cat.value) }">
            <div class="category-subs-inner">
              <div
                v-for="sub in cat.tags"
                :key="sub.value"
                class="category-subitem"
                :class="{ active: isActiveCategory(cat.value, sub.value) }"
                @click="selectCategory(cat.value, sub.value)"
              >
                └ {{ sub.label }} <span class="category-count">({{ postsStore.tagCount(sub.value) }})</span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="sidebar-footer">
      <div class="inquiry-nav-item" @click="showInquiryModal = true">💬 문의하기</div>

      <div v-if="authStore.user" class="profile-block">
        <div class="profile">
          <img
            v-if="authStore.user.profileImg"
            class="avatar avatar-img"
            :src="authStore.user.profileImg"
            :alt="authStore.user.name"
          />
          <div v-else class="avatar">{{ userInitial }}</div>
          <div>
            <div class="profile-name">{{ authStore.user.name }}</div>
            <div class="profile-meta">{{ userMeta }}</div>
          </div>
        </div>
        <div class="logout-link" @click="handleLogout">로그아웃</div>
      </div>
    </div>
  </aside>

  <InquiryModal v-if="showInquiryModal" @close="showInquiryModal = false" />
</template>

<style scoped>
.sidebar {
  width: 260px;
  max-width: 85vw;
  flex-shrink: 0;
  background: #ffffff;
  border-right: 1px solid rgba(26, 26, 46, 0.06);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px 20px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.logo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 40px 8px;
}

.logo {
  display: flex;
  align-items: center;
  text-decoration: none;
}

.collapse-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid rgba(26, 26, 46, 0.15);
  background: #ffffff;
  color: #1a1a2e;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.collapse-btn:hover {
  background: rgba(26, 26, 46, 0.05);
  color: #4a3f8f;
}

/* 접기 버튼은 데스크톱 전용 - 모바일은 오버레이 드로어(닫기는 바깥 클릭)만 사용.
   AppLayout.vue의 사이드바 드로어 전환 기준(900px)과 맞춘다 */
@media (max-width: 900px) {
  .collapse-btn {
    display: none;
  }
}

.logo-text {
  font-size: 21px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: #1a1a2e;
}

.logo-accent {
  color: #4a3f8f;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-bottom: 12px;
}

/* 접기/펼치기 화살표가 있는 메뉴(피드/마이페이지) - 카테고리 행과 동일하게 라벨 왼쪽, 화살표 오른쪽 끝 */
.nav-item.has-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.nav-item {
  padding: 7px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 580;
  color: #1a1a2e;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.15s ease;
}

.nav-item:hover:not(.active) {
  background: rgba(26, 26, 46, 0.05);
}

.nav-item.active {
  background: #f1eefc;
  color: #4a3f8f;
  font-weight: 700;
}

.admin-nav-item {
  color: #e0607d;
}

.admin-nav-item:hover:not(.active) {
  background: rgba(224, 96, 125, 0.08);
}

.admin-nav-item.active {
  background: #fbe9ee;
  color: #e0607d;
}

.divider {
  height: 1px;
  background: rgba(26, 26, 46, 0.06);
  margin: 6px 0 10px;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.category-item,
.category-subitem {
  padding: 7px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #1a1a2e;
  cursor: pointer;
  transition: background 0.15s ease;
}

.category-item {
  font-weight: 580;
  /* 라벨은 왼쪽, 접기/펼치기 화살표는 오른쪽 끝 */
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.category-item:hover:not(.active),
.category-subitem:hover:not(.active) {
  background: rgba(26, 26, 46, 0.05);
}

.category-subitem {
  padding: 3px 14px 4px 30px;
  font-size: 12px;
  color: #636e72;
}

/* 부모 카테고리 바로 다음에 오는 첫 하위항목만 간격을 더 좁혀서 그 카테고리 소속처럼 붙어보이게 함 */
.category-subs-inner > .category-subitem:first-child {
  padding-top: 1px;
}

/* 하위 항목 묶음 - grid 행 높이(1fr → 0fr) 전환으로 높이를 몰라도 부드럽게 접힘 */
.category-subs {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 0.2s ease;
}

.category-subs-inner {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.category-subs.collapsed {
  grid-template-rows: 0fr;
}

/* 접힌 상태의 하위 항목은 화면 낭독기/탭 이동에서도 빠지도록 전환이 끝난 뒤 숨김 */
.category-subs.collapsed .category-subs-inner {
  visibility: hidden;
  transition: visibility 0s 0.2s;
}

/* 누르는 영역을 크게 - 행의 위/아래/오른쪽 padding까지 덮도록 음수 margin으로 넓혀서(행 높이 x 오른쪽 34px)
   화살표가 작아도 잘 눌리게 함. 행 크기와 배치는 그대로 */
.category-toggle {
  flex-shrink: 0;
  align-self: stretch;
  width: 34px;
  margin: -7px -12px -7px 0;
  padding: 0;
  border: none;
  border-radius: 0 9px 9px 0;
  background: transparent;
  color: #636e72;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.category-toggle:hover {
  background: rgba(26, 26, 46, 0.07);
}

.category-toggle svg {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: transform 0.2s ease;
}

.category-toggle.collapsed svg {
  transform: rotate(-90deg);
}

/* 접힌 카테고리 안에 선택된 하위 태그가 있을 때 - active보다 연한 강조 */
.category-item.has-hidden-active:not(.active) {
  background: rgba(241, 238, 252, 0.6);
  color: #4a3f8f;
}

@media (prefers-reduced-motion: reduce) {
  .category-subs,
  .category-toggle svg {
    transition: none;
  }
}

/* 하위 메뉴 앞 SK 로고 - 12px 글씨의 이모지와 비슷한 크기로 맞춤 */
.subitem-logo {
  width: 13px;
  height: 13px;
  object-fit: contain;
  vertical-align: -2px;
}

.category-count {
  color: #636e72;
  font-size: 12px;
}

.category-item.active .category-count,
.category-subitem.active .category-count {
  color: #4a3f8f;
}

.category-item.active,
.category-subitem.active {
  background: #f1eefc;
  color: #4a3f8f;
  font-weight: 700;
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.inquiry-nav-item {
  padding: 7px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 580;
  color: #1a1a2e;
  cursor: pointer;
  transition: background 0.15s ease;
}

.inquiry-nav-item:hover {
  background: rgba(26, 26, 46, 0.05);
}

.profile-block {
  padding: 4px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px;
  border-radius: 12px;
  background: #fafafa;
}

.logout-link {
  margin-top: 6px;
  text-align: center;
  font-size: 11.5px;
  color: #636e72;
  cursor: pointer;
}

.logout-link:hover {
  color: #4a3f8f;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6c5ce7, #4a3f8f);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 13px;
}

.avatar-img {
  object-fit: cover;
}

.profile-name {
  font-size: 13px;
  font-weight: 700;
  color: #1a1a2e;
}

.profile-meta {
  font-size: 11.5px;
  color: #636e72;
}
</style>
