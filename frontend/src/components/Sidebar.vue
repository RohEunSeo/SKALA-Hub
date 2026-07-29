<script setup>
// 좌측 네비게이션 사이드바 - 로고/메뉴/카테고리/유저 프로필
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { computed, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { CATEGORIES } from '../constants/categories'

const emit = defineEmits(['navigate'])

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()

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
  postsStore.setCategory(value, tagValue)
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
      <RouterLink to="/" class="logo">
        <span class="logo-text">SKALA<span class="logo-accent">Hub</span></span>
      </RouterLink>

      <nav class="nav">
        <RouterLink to="/" class="nav-item" :class="{ active: route.name === 'home' }">🏠 홈</RouterLink>
        <RouterLink to="/feed" class="nav-item" :class="{ active: route.name === 'feed' }">📋 피드</RouterLink>
        <RouterLink to="/mypage" class="nav-item" :class="{ active: route.name === 'mypage' }"
          >👤 마이페이지</RouterLink
        >
        <RouterLink
          v-if="authStore.user?.role === 'admin'"
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
            └ {{ sub.label }}
          </div>
        </template>
      </div>
    </div>

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
  </aside>
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
  padding: 24px 20px;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.logo {
  display: flex;
  align-items: center;
  padding: 4px 8px 24px;
  text-decoration: none;
}

.logo-text {
  font-size: 21px;
  font-weight: 900;
  letter-spacing: -0.5px;
  color: #1a1a2e;
}

.logo-accent {
  color: #4a3f8f;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 20px;
}

.nav-item {
  padding: 9px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #1a1a2e;
  text-decoration: none;
  cursor: pointer;
}

.nav-item.active {
  background: #f1eefc;
  color: #4a3f8f;
  font-weight: 700;
}

.admin-nav-item {
  color: #e0607d;
}

.admin-nav-item.active {
  background: #fbe9ee;
  color: #e0607d;
}

.divider {
  height: 1px;
  background: rgba(26, 26, 46, 0.06);
  margin: 8px 0 16px;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.category-item,
.category-subitem {
  padding: 9px 12px;
  border-radius: 9px;
  font-size: 13.5px;
  color: #1a1a2e;
  cursor: pointer;
}

.category-subitem {
  padding-left: 26px;
  font-size: 12.5px;
  color: #636e72;
}

.category-count {
  color: #636e72;
  font-size: 12px;
}

.category-item.active .category-count {
  color: #4a3f8f;
}

.category-item.active,
.category-subitem.active {
  background: #f1eefc;
  color: #4a3f8f;
  font-weight: 700;
}

.profile-block {
  padding: 4px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: #fafafa;
}

.logout-link {
  margin-top: 8px;
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
