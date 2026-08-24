<script setup>
// 좌측 네비게이션 사이드바 - 로고/메뉴/카테고리/유저 프로필
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { CATEGORIES } from '../constants/categories'
import InquiryModal from './InquiryModal.vue'

const emit = defineEmits(['navigate', 'collapse'])

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()
const showInquiryModal = ref(false)

onMounted(() => {
  postsStore.loadCategoryCounts()
})

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
        <RouterLink to="/feed" class="nav-item" :class="{ active: route.name === 'feed' }">📋 피드</RouterLink>
        <RouterLink to="/mypage" class="nav-item" :class="{ active: route.name === 'mypage' }"
          >👤 마이페이지</RouterLink
        >
        <RouterLink
          v-if="authStore.effectiveIsAdmin"
          to="/dashboard"
          class="nav-item"
          :class="{ active: route.name === 'dashboard' }"
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
            :class="{ active: isActiveCategory(cat.value) }"
            @click="selectCategory(cat.value)"
          >
            {{ cat.icon }} {{ cat.label }} <span class="category-count">({{ postsStore.categoryCount(cat.value) }})</span>
          </div>
          <div
            v-for="sub in cat.tags"
            :key="sub.value"
            class="category-subitem"
            :class="{ active: isActiveCategory(cat.value, sub.value) }"
            @click="selectCategory(cat.value, sub.value)"
          >
            └ {{ sub.label }} <span class="category-count">({{ postsStore.tagCount(sub.value) }})</span>
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

/* 접기 버튼은 데스크톱 전용 - 모바일은 오버레이 드로어(닫기는 바깥 클릭)만 사용 */
@media (max-width: 768px) {
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
.category-item + .category-subitem {
  padding-top: 1px;
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
