<script setup>
// 커뮤니티 > 공유 드라이브 목록 - 사람들이 push한 폴더(레포)를 둘러보고 클론하는 곳
import { computed, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import CommunityTabs from '../components/community/CommunityTabs.vue'
import RepoCard from '../components/community/RepoCard.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunityStore } from '../stores/community'
import { useToastStore } from '../stores/toast'
import { CATEGORIES } from '../constants/categories'

const authStore = useAuthStore()
const store = useCommunityStore()
const toast = useToastStore()

const q = ref('')
const sort = ref('popular')
const topic = ref('')

const list = computed(() => {
  const kw = q.value.trim().toLowerCase()
  const arr = store.repos.filter(
    (r) => (!topic.value || r.topics.includes(topic.value)) && (!kw || `${r.owner}/${r.name} ${r.desc}`.toLowerCase().includes(kw)),
  )
  const by = { popular: (r) => store.starCount(r), clone: (r) => r.forks, recent: (r) => -r.commits }
  return arr.sort((a, b) => by[sort.value](b) - by[sort.value](a))
})
// 이번 주 많이 클론된 레포 TOP3
const hot = computed(() => [...store.repos].sort((a, b) => b.forks - a.forks).slice(0, 3))
</script>

<template>
  <AppLayout :max-width="1100">
    <AuthRequired v-if="!authStore.isAuthenticated" message="커뮤니티는 로그인이 필요합니다" />
    <template v-else>
      <CommunityTabs active="drive" />
      <div class="dl-top">
        <div>
          <h1 class="dl-title">공유 드라이브</h1>
          <p class="dl-sub">교육생들이 모아둔 글 폴더를 둘러보고, 내 폴더로 clone 해보세요</p>
        </div>
        <button class="dl-push" @click="store.openPush(null)">↑ Push</button>
      </div>

      <h2 class="dl-h">🔥 이번 주 많이 clone된 레포</h2>
      <div class="dl-grid"><RepoCard v-for="r in hot" :key="r.id" :repo="r" /></div>

      <h2 class="dl-h">전체 레포 <span class="dl-count">{{ list.length }}</span></h2>
      <div class="dl-tools">
        <input v-model="q" class="dl-search" placeholder="레포 검색 (이름, 설명)" />
        <select v-model="sort" class="dl-sort">
          <option value="popular">★ 인기순</option>
          <option value="clone">⑂ clone 많은 순</option>
          <option value="recent">🕘 최근 활동순</option>
        </select>
      </div>
      <div class="dl-chips">
        <button class="dl-chip" :class="{ on: !topic }" @click="topic = ''">전체</button>
        <button v-for="c in CATEGORIES" :key="c.value" class="dl-chip" :class="{ on: topic === c.label }" @click="topic = c.label">{{ c.icon }} {{ c.label }}</button>
      </div>
      <div class="dl-grid"><RepoCard v-for="r in list" :key="r.id" :repo="r" /></div>
      <p v-if="!list.length" class="dl-empty">조건에 맞는 레포가 없어요</p>
    </template>
  </AppLayout>
</template>

<style scoped>
.dl-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.dl-title { margin: 0; font-size: 22px; color: #1a1a2e; }
.dl-sub { margin: 4px 0 0; font-size: 13px; color: #636e72; }
.dl-push { padding: 8px 18px; border: none; border-radius: 8px; background: #4a3f8f; color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; }
.dl-push:hover { background: #6c5ce7; }
.dl-h { margin: 24px 0 12px; font-size: 15px; color: #1a1a2e; }
.dl-count { margin-left: 4px; padding: 1px 8px; border-radius: 10px; background: #efeff6; font-size: 12px; color: #636e72; }
.dl-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.dl-tools { display: flex; gap: 8px; margin-bottom: 10px; }
.dl-search { flex: 1; padding: 8px 12px; border: 1px solid #d8d8e4; border-radius: 8px; font-size: 14px; outline: none; }
.dl-search:focus { border-color: #6c5ce7; }
.dl-sort { padding: 8px 10px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; font-size: 13px; }
.dl-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
.dl-chip { padding: 5px 12px; border: 1px solid #d8d8e4; border-radius: 16px; background: #fff; font-size: 13px; color: #636e72; cursor: pointer; }
.dl-chip.on { border-color: #4a3f8f; background: #4a3f8f; color: #fff; }
.dl-empty { padding: 40px 0; text-align: center; color: #636e72; }
</style>
