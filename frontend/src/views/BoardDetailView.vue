<script setup>
// 커뮤니티 > 게시판 글 상세 - 본문 + 첨부(레포/글) + 좋아요/공유 + 댓글(답변 채택, 레포로 답하기)
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import AttachmentCard from '../components/community/AttachmentCard.vue'
import CommentThread from '../components/community/CommentThread.vue'
import UserDot from '../components/community/UserDot.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunityStore } from '../stores/community'
import { BOARD_TYPES, MOCK_COMMENTS } from '../mock/community'
import { miniMarkdown } from '../utils/miniMarkdown'

const route = useRoute()
const authStore = useAuthStore()
const store = useCommunityStore()

const post = computed(() => store.boardPost(route.params.id))
const type = computed(() => BOARD_TYPES.find((t) => t.value === post.value?.type))
const html = computed(() => miniMarkdown((post.value?.body ?? '').replace(/\n/g, '\n\n')))
const answerable = computed(() => ['request', 'qna'].includes(post.value?.type))
// 채택된 댓글 id: mock 시드의 adopted 또는 내가 채택한 것
const adoptedId = computed(() => {
  if (!post.value) return ''
  const mine = store.adoptedOf(post.value.id)
  if (typeof mine === 'string') return mine
  return (MOCK_COMMENTS[post.value.id] ?? []).find((c) => c.adopted)?.id ?? ''
})
</script>

<template>
  <AppLayout :max-width="860">
    <AuthRequired v-if="!authStore.isAuthenticated" message="커뮤니티는 로그인이 필요합니다" />
    <div v-else-if="!post" class="bdd-none">글을 찾을 수 없어요 · <RouterLink to="/community/board">게시판으로</RouterLink></div>
    <article v-else class="bdd">
      <RouterLink to="/community/board" class="bdd-back">‹ 게시판</RouterLink>
      <div class="bdd-head">
        <span class="bdd-type">{{ type.icon }} {{ type.label }}</span>
        <span v-if="answerable" class="bdd-st" :class="post.solved ? 'done' : 'wait'">{{ post.solved ? '✓ 해결됨' : '답변 대기' }}</span>
      </div>
      <h1 class="bdd-title">{{ post.title }}</h1>
      <div class="bdd-meta"><UserDot :user="store.userOf(post.by)" :size="22" /> <b>{{ store.userOf(post.by).name }}</b> <span>{{ store.userOf(post.by).cohort }} · {{ post.ago }}</span></div>
      <div class="bdd-body md" v-html="html"></div>
      <AttachmentCard v-if="post.attach" :attach="post.attach" />
      <div class="bdd-act">
        <button :class="{ on: store.isLiked(post.id) }" @click="store.toggleLike(post.id)">👍 좋아요 {{ store.likeCount(post) }}</button>
      </div>
      <CommentThread
        :comment-key="`board:${post.id}`"
        :seed="MOCK_COMMENTS[post.id] ?? []"
        :can-adopt="answerable && post.by === 'me'"
        :adopted-id="adoptedId"
        :allow-repo="answerable"
        @adopt="(id) => store.adopt(post.id, id)"
      />
    </article>
  </AppLayout>
</template>

<style scoped>
.bdd-none { padding: 80px 0; text-align: center; color: #636e72; }
.bdd-back { font-size: 13px; color: #636e72; text-decoration: none; }
.bdd-head { display: flex; align-items: center; gap: 8px; margin: 14px 0 6px; }
.bdd-type { padding: 2px 10px; border-radius: 12px; background: #efeaff; color: #4a3f8f; font-size: 12px; font-weight: 600; }
.bdd-st { padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.bdd-st.done { background: #dafbe1; color: #1a7f37; }
.bdd-st.wait { background: #eaeef2; color: #636e72; }
.bdd-title { margin: 0 0 10px; font-size: 24px; color: #1a1a2e; }
.bdd-meta { display: flex; align-items: center; gap: 8px; font-size: 13px; padding-bottom: 14px; border-bottom: 1px solid #eaeef2; }
.bdd-meta span { color: #8a8fa0; }
.bdd-body { padding: 18px 0 6px; font-size: 15px; line-height: 1.8; }
.bdd-body :deep(p) { margin: 0 0 4px; }
.bdd-body :deep(ul) { padding-left: 22px; }
.bdd-body :deep(code) { padding: 1px 6px; border-radius: 4px; background: #f7e0d9; color: #e01e5a; font-size: 13px; }
.bdd-act { display: flex; gap: 8px; margin: 16px 0 28px; }
.bdd-act button { padding: 7px 16px; border: 1px solid #d8d8e4; border-radius: 18px; background: #fff; font-size: 13px; cursor: pointer; }
.bdd-act button.on { border-color: #6c5ce7; background: #f3f0ff; color: #4a3f8f; }
</style>
