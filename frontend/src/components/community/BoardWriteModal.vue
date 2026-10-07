<script setup>
// 게시판 글쓰기 모달 - 말머리/제목/본문 + Hub 글·내 레포 첨부 (정보요청에 레포를 붙이거나, 꿀팁으로 레포를 소개)
import { computed, ref } from 'vue'
import { BOARD_TYPES } from '../../mock/community'
import { useAuthStore } from '../../stores/auth'
import { useCommunityStore } from '../../stores/community'
import AddPostsModal from './AddPostsModal.vue'
import AttachmentCard from './AttachmentCard.vue'

const emit = defineEmits(['close', 'created'])
const auth = useAuthStore()
const store = useCommunityStore()

// 공지는 관리자만 (authStore.effectiveIsAdmin 하나로 통제)
const types = computed(() => BOARD_TYPES.filter((t) => !t.adminOnly || auth.effectiveIsAdmin))
const type = ref('request')
const title = ref('')
const body = ref('')
const attach = ref(null)
const picking = ref(false)
const repoId = ref('')

function pickPost({ items }) {
  const p = items[0]
  attach.value = { kind: 'post', title: p.title, postId: p.postId }
  picking.value = false
}
function pickRepo() {
  attach.value = repoId.value ? { kind: 'repo', id: repoId.value } : null
}
function submit() {
  if (!title.value.trim() || !body.value.trim()) return
  emit('created', store.createBoard({ type: type.value, title: title.value.trim(), body: body.value.trim(), attach: attach.value }))
}
</script>

<template>
  <Teleport to="body">
    <div class="bw-overlay" @click.self="emit('close')">
      <div class="bw" role="dialog" aria-modal="true">
        <div class="bw-head"><b>✏ 글쓰기</b><button aria-label="닫기" @click="emit('close')">✕</button></div>
        <div class="bw-types">
          <button v-for="t in types" :key="t.value" :class="{ on: type === t.value }" @click="type = t.value">{{ t.icon }} {{ t.label }}</button>
        </div>
        <input v-model="title" maxlength="80" placeholder="제목" />
        <textarea v-model="body" rows="7" placeholder="내용을 입력하세요. 정보요청이라면 어떤 자료가 필요한지 구체적으로 적어주세요."></textarea>
        <div class="bw-attach">
          <AttachmentCard v-if="attach" :attach="attach" />
          <div v-else class="bw-opts">
            <button @click="picking = true">📄 내 Hub 글 첨부</button>
            <select v-model="repoId" @change="pickRepo">
              <option value="">🗂 드라이브 레포 첨부…</option>
              <option v-for="r in store.repos" :key="r.id" :value="r.id">{{ r.owner }} / {{ r.name }}</option>
            </select>
          </div>
          <button v-if="attach" class="bw-rm" @click="attach = null; repoId = ''">첨부 제거</button>
        </div>
        <div class="bw-foot">
          <button class="bw-cancel" @click="emit('close')">취소</button>
          <button class="bw-ok" :disabled="!title.trim() || !body.trim()" @click="submit">등록</button>
        </div>
      </div>
    </div>
    <AddPostsModal v-if="picking" mode="pick" @close="picking = false" @submit="pickPost" />
  </Teleport>
</template>

<style scoped>
.bw-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(26, 26, 46, 0.45); }
.bw { width: 100%; max-width: 600px; max-height: 90vh; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding: 20px; background: #fff; border-radius: 12px; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25); }
.bw-head { display: flex; align-items: center; justify-content: space-between; font-size: 16px; }
.bw-head button { border: none; background: none; font-size: 16px; cursor: pointer; }
.bw-types { display: flex; flex-wrap: wrap; gap: 6px; }
.bw-types button { padding: 5px 12px; border: 1px solid #d8d8e4; border-radius: 16px; background: #fff; font-size: 13px; cursor: pointer; }
.bw-types button.on { border-color: #4a3f8f; background: #4a3f8f; color: #fff; }
.bw input, .bw textarea { padding: 10px 12px; border: 1px solid #d8d8e4; border-radius: 8px; font: 14px/1.6 inherit; outline: none; resize: vertical; }
.bw input:focus, .bw textarea:focus { border-color: #6c5ce7; }
.bw-opts { display: flex; flex-wrap: wrap; gap: 8px; }
.bw-opts button, .bw-opts select { padding: 7px 12px; border: 1px dashed #b7a6e6; border-radius: 8px; background: #fbfaff; font-size: 13px; color: #4a3f8f; cursor: pointer; }
.bw-rm { margin-top: 6px; border: none; background: none; font-size: 12px; color: #636e72; cursor: pointer; }
.bw-foot { display: flex; justify-content: flex-end; gap: 8px; }
.bw-cancel { padding: 8px 14px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; font-size: 13px; cursor: pointer; }
.bw-ok { padding: 8px 18px; border: none; border-radius: 8px; background: #4a3f8f; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.bw-ok:disabled { opacity: 0.4; cursor: default; }
</style>
