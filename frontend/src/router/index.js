// 앱 라우팅 설정
import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue') },
    { path: '/mypage', name: 'mypage', component: () => import('../views/MyPageView.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue') },
  ],
})

export default router
