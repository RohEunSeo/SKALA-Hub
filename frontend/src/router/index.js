// 앱 라우팅 설정
import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '../stores/auth'

// 별도 로그인 페이지 없음 - 누구나 홈은 볼 수 있고, 로그인이 필요한 화면은
// 각 화면 컴포넌트 내부에서 authStore.isAuthenticated를 보고 AuthRequired를 보여준다
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  // 뒤로가기(popstate)로 돌아왔을 때는 떠나기 직전 스크롤 위치로, 그 외(새 페이지 이동)는 맨 위로
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  },
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue') },
    {
      path: '/posts/:id',
      name: 'post-detail',
      component: () => import('../views/PostDetailView.vue'),
    },
    { path: '/mypage', name: 'mypage', component: () => import('../views/MyPageView.vue') },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/community', redirect: '/community/drive' },
    { path: '/community/board', name: 'community-board', meta: { adminOnly: true }, component: () => import('../views/BoardView.vue') },
    { path: '/community/board/:id', name: 'community-post', meta: { adminOnly: true }, component: () => import('../views/BoardDetailView.vue') },
    { path: '/community/drive', name: 'community-drive', meta: { adminOnly: true }, component: () => import('../views/DriveListView.vue') },
    {
      path: '/community/drive/:owner/:repo',
      name: 'community-repo', meta: { adminOnly: true },
      component: () => import('../views/RepoDetailView.vue'),
    },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue') },
  ],
})

// 커뮤니티는 아직 배포 전 기능 - 관리자(미리보기 모드 포함)가 아니면 홈으로 돌려보냄
router.beforeEach((to) => {
  if (to.meta.adminOnly && !useAuthStore().effectiveIsAdmin) return { name: 'home' }
})

export default router
