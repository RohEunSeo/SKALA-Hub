<script setup>
// 댓글 스레드 (게시판/이슈/PR 공용) - 1단 대댓글, 답변 채택, 내 레포로 답하기
import { computed, ref } from 'vue'
import { useCommunityStore } from '../../stores/community'
import UserDot from './UserDot.vue'
import AttachmentCard from './AttachmentCard.vue'

const props = defineProps({
  commentKey: { type: String, required: true }, // 댓글 저장 키 (board:id / issue:repo#n / pr:repo#n)
  seed: { type: Array, default: () => [] },
  canAdopt: { type: Boolean, default: false }, // 글쓴이가 정보요청/Q&A 답변을 채택할 수 있는지
  adoptedId: { type: String, default: '' },
  allowRepo: { type: Boolean, default: false }, // 🗂 레포로 답하기
})
const emit = defineEmits(['adopt'])
const store = useCommunityStore()

const list = computed(() => store.commentsOf(props.commentKey, props.seed))
const tops = computed(() => list.value.filter((c) => !c.parent))
const repliesOf = (id) => list.value.filter((c) => c.parent === id)

const text = ref('')
const replyTo = ref('')
const attachId = ref('') // 첨부할 내 레포 id
const showAttach = ref(false)

function submit() {
  const t = text.value.trim()
  if (!t && !attachId.value) return
  store.addComment(props.commentKey, {
    text: t || '답변 레포를 첨부했어요',
    parent: replyTo.value || null,
    attach: attachId.value ? { kind: 'repo', id: attachId.value } : null,
  })
  text.value = ''
  replyTo.value = ''
  attachId.value = ''
  showAttach.value = false
}
</script>

<template>
  <div class="ct">
    <h3 class="ct-h">💬 댓글 {{ list.length }}</h3>
    <div v-for="c in tops" :key="c.id" class="c-block">
      <div class="c" :class="{ adopted: adoptedId === c.id }">
        <UserDot :user="store.userOf(c.by)" :size="28" />
        <div class="c-main">
          <div class="c-head">
            <b>{{ store.userOf(c.by).name }}</b><span class="c-ago">{{ c.ago }}</span>
            <span v-if="adoptedId === c.id" class="c-adopt">✓ 채택된 답변</span>
          </div>
          <p class="c-text">{{ c.text }}</p>
          <AttachmentCard v-if="c.attach" :attach="c.attach" />
          <div class="c-act">
            <button @click="replyTo = replyTo === c.id ? '' : c.id">↩ 답글</button>
            <button v-if="canAdopt && !adoptedId" class="adopt" @click="emit('adopt', c.id)">✓ 이 답변 채택</button>
          </div>
        </div>
      </div>
      <div v-for="r in repliesOf(c.id)" :key="r.id" class="c reply">
        <UserDot :user="store.userOf(r.by)" :size="24" />
        <div class="c-main">
          <div class="c-head"><b>{{ store.userOf(r.by).name }}</b><span class="c-ago">{{ r.ago }}</span></div>
          <p class="c-text">{{ r.text }}</p>
          <AttachmentCard v-if="r.attach" :attach="r.attach" />
        </div>
      </div>
    </div>
    <p v-if="!tops.length" class="c-empty">아직 댓글이 없어요. 첫 댓글을 남겨보세요!</p>

    <div class="c-form">
      <div v-if="replyTo" class="c-replying">답글 작성 중 <button @click="replyTo = ''">취소</button></div>
      <textarea v-model="text" rows="3" placeholder="댓글을 입력하세요" @keydown.meta.enter="submit" @keydown.ctrl.enter="submit"></textarea>
      <div v-if="showAttach" class="c-attach">
        <select v-model="attachId">
          <option value="">내 레포 선택…</option>
          <option v-for="r in store.myRepoList" :key="r.id" :value="r.id">{{ r.name }}</option>
        </select>
        <span v-if="!store.myRepoList.length" class="c-hint">아직 push한 레포가 없어요</span>
      </div>
      <div class="c-foot">
        <button v-if="allowRepo" class="c-btn" @click="showAttach = !showAttach">🗂 레포로 답하기</button>
        <button class="c-submit" :disabled="!text.trim() && !attachId" @click="submit">댓글 남기기</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ct-h { margin: 0 0 12px; font-size: 15px; }
.c-block { margin-bottom: 14px; }
.c { display: flex; gap: 10px; padding: 12px 14px; border: 1px solid #eaeef2; border-radius: 8px; background: #fff; }
.c.adopted { border-color: #1a7f37; background: #f3fcf5; }
.c.reply { margin: 6px 0 0 36px; background: #fafafa; }
.c-main { flex: 1; min-width: 0; }
.c-head { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.c-ago { color: #8a8fa0; font-size: 12px; }
.c-adopt { margin-left: auto; padding: 1px 8px; border-radius: 10px; background: #dafbe1; color: #1a7f37; font-size: 12px; font-weight: 600; }
.c-text { margin: 6px 0 0; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.c-act { display: flex; gap: 10px; margin-top: 8px; }
.c-act button { border: none; background: none; padding: 0; font-size: 12px; color: #636e72; cursor: pointer; }
.c-act button:hover { color: #4a3f8f; }
.c-act .adopt { color: #1a7f37; font-weight: 600; }
.c-empty { padding: 20px 0; text-align: center; font-size: 13px; color: #8a8fa0; }
.c-form { margin-top: 12px; padding: 12px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; }
.c-form textarea { width: 100%; box-sizing: border-box; border: none; outline: none; resize: vertical; font: 14px/1.6 inherit; }
.c-replying { margin-bottom: 6px; font-size: 12px; color: #6c5ce7; }
.c-replying button { border: none; background: none; color: #636e72; cursor: pointer; font-size: 12px; }
.c-attach { margin: 6px 0; display: flex; align-items: center; gap: 8px; }
.c-attach select { padding: 5px 8px; border: 1px solid #d8d8e4; border-radius: 6px; font-size: 13px; }
.c-hint { font-size: 12px; color: #8a8fa0; }
.c-foot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 6px; }
.c-btn { padding: 6px 12px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; font-size: 13px; cursor: pointer; }
.c-submit { padding: 6px 16px; border: none; border-radius: 8px; background: #4a3f8f; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.c-submit:disabled { opacity: 0.4; cursor: default; }
</style>
