<script setup>
// 커뮤니티 > 게시판 목록 - 말머리 칩, 고정 공지, 정보요청 해결됨/답변 대기 뱃지
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import CommunityTabs from '../components/community/CommunityTabs.vue'
import BoardWriteModal from '../components/community/BoardWriteModal.vue'
import UserDot from '../components/community/UserDot.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunityStore } from '../stores/community'
import { BOARD_TYPES } from '../mock/community'

const authStore = useAuthStore()
const store = useCommunityStore()
const router = useRouter()

const type = ref('')
const q = ref('')
const writing = ref(false)
const typeOf = (v) => BOARD_TYPES.find((t) => t.value === v)

const list = computed(() => {
  const kw = q.value.trim().toLowerCase()
  const rows = store.board.filter((p) => (!type.value || p.type === type.value) && (!kw || `${p.title} ${p.body}`.toLowerCase().includes(kw)))
  return rows.sort((a, b) => Number(!!b.pinned) - Number(!!a.pinned)) // 공지 고정글 맨 위 (나머지는 등록 순서 유지)
})
const statusOf = (p) => (p.type === 'request' || p.type === 'qna' ? (p.solved ? 'done' : 'wait') : '')
const open = (p) => router.push(`/community/board/${p.id}`)
</script>

<template>
  <AppLayout :max-width="1100">
    <AuthRequired v-if="!authStore.isAuthenticated" message="커뮤니티는 로그인이 필요합니다" />
    <template v-else>
      <CommunityTabs active="board" />
      <div class="bd-top">
        <div>
          <h1 class="bd-title">게시판</h1>
          <p class="bd-sub">궁금한 점, 필요한 자료, 유용했던 글을 자유롭게 나눠요</p>
        </div>
        <button class="bd-write" @click="writing = true">✏ 글쓰기</button>
      </div>

      <div class="bd-tools">
        <div class="bd-chips">
          <button class="bd-chip" :class="{ on: !type }" @click="type = ''">전체</button>
          <button v-for="t in BOARD_TYPES" :key="t.value" class="bd-chip" :class="{ on: type === t.value }" @click="type = t.value">{{ t.icon }} {{ t.label }}</button>
        </div>
        <input v-model="q" class="bd-search" placeholder="글 검색" />
      </div>

      <div class="bd-list">
        <div v-for="p in list" :key="p.id" class="bd-row" :class="{ pinned: p.pinned }" role="button" tabindex="0" @click="open(p)" @keydown.enter="open(p)">
          <span class="bd-type" :class="p.type">{{ typeOf(p.type).icon }} {{ typeOf(p.type).label }}</span>
          <div class="bd-main">
            <div class="bd-t">
              <span v-if="p.pinned" class="bd-pin">📌</span>{{ p.title }}
              <span v-if="statusOf(p)" class="bd-st" :class="statusOf(p)">{{ statusOf(p) === 'done' ? '✓ 해결됨' : '답변 대기' }}</span>
              <span v-if="p.attach" class="bd-att">{{ p.attach.kind === 'repo' ? '🗂' : '📄' }}</span>
            </div>
            <div class="bd-meta"><UserDot :user="store.userOf(p.by)" :size="16" /> {{ store.userOf(p.by).name }} · {{ p.ago }}</div>
          </div>
          <span class="bd-num">👍 {{ store.likeCount(p) }}</span>
          <span class="bd-num">💬 {{ store.boardCommentCount(p.id) }}</span>
        </div>
        <p v-if="!list.length" class="bd-empty">조건에 맞는 글이 없어요</p>
      </div>

      <BoardWriteModal v-if="writing" @close="writing = false" @created="(p) => { writing = false; open(p) }" />
    </template>
  </AppLayout>
</template>

<style scoped>
.bd-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.bd-title { margin: 0; font-size: 22px; color: #1a1a2e; }
.bd-sub { margin: 4px 0 0; font-size: 13px; color: #636e72; }
.bd-write { padding: 8px 18px; border: none; border-radius: 8px; background: #4a3f8f; color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; }
.bd-write:hover { background: #6c5ce7; }
.bd-tools { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; }
.bd-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.bd-chip { padding: 5px 12px; border: 1px solid #d8d8e4; border-radius: 16px; background: #fff; font-size: 13px; color: #636e72; cursor: pointer; }
.bd-chip.on { border-color: #4a3f8f; background: #4a3f8f; color: #fff; }
.bd-search { padding: 7px 12px; border: 1px solid #d8d8e4; border-radius: 8px; font-size: 13px; outline: none; }
.bd-search:focus { border-color: #6c5ce7; }
.bd-list { border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; overflow: hidden; }
.bd-row { display: flex; align-items: center; gap: 14px; padding: 14px 16px; border-top: 1px solid #eaeef2; cursor: pointer; }
.bd-row:first-child { border-top: none; }
.bd-row:hover { background: #fbfaff; }
.bd-row.pinned { background: #faf8ff; }
.bd-type { flex: none; width: 76px; padding: 3px 0; border-radius: 12px; background: #efeff6; font-size: 12px; font-weight: 600; text-align: center; color: #636e72; }
.bd-type.notice { background: #ffe9ee; color: #c2294d; }
.bd-type.request { background: #e1f0fb; color: #1264a3; }
.bd-type.tip { background: #fff4d6; color: #a06b00; }
.bd-type.qna { background: #efeaff; color: #4a3f8f; }
.bd-main { flex: 1; min-width: 0; }
.bd-t { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; color: #1a1a2e; }
.bd-pin { font-size: 12px; }
.bd-st { padding: 1px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.bd-st.done { background: #dafbe1; color: #1a7f37; }
.bd-st.wait { background: #eaeef2; color: #636e72; }
.bd-att { font-size: 12px; }
.bd-meta { display: flex; align-items: center; gap: 5px; margin-top: 4px; font-size: 12px; color: #8a8fa0; }
.bd-num { flex: none; font-size: 12px; color: #636e72; }
.bd-empty { padding: 40px 0; text-align: center; color: #636e72; }
@media (max-width: 640px) { .bd-type { width: auto; padding: 3px 8px; } .bd-num { display: none; } }
</style>
