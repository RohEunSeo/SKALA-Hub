<script setup>
// Push 모달 - 내 폴더를 공유 드라이브 레포로 올리기: ① 폴더 선택 → ② 이름/설명/공개범위/기여 방식 → push
// AppLayout에 한 번만 마운트, store.openPush(folderId)로 어디서든 열림 (드라이브 목록 / 내 폴더 바)
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useCommunityStore } from '../../stores/community'
import { useFoldersStore } from '../../stores/folders'
import { useAuthStore } from '../../stores/auth'
import { fetchPost } from '../../api/posts'
import FolderIcon from '../FolderIcon.vue'
import SaveLoader from '../chat/SaveLoader.vue'

const store = useCommunityStore()
const folders = useFoldersStore()
const auth = useAuthStore()
const router = useRouter()

const open = computed(() => store.pushFolderId !== undefined)
const folderId = ref(null)
const name = ref('')
const desc = ref('')
const visibility = ref('Public')
const mode = ref('pr')
const items = ref([])
const loading = ref(false)
const phase = ref('form') // form | pushing | done
const result = ref(null)

const folder = computed(() => folders.byId(folderId.value))
const exists = computed(() => store.repos.some((r) => r.mine && r.name === name.value.trim()))

watch(() => store.pushFolderId, (id) => {
  if (id === undefined) return
  phase.value = 'form'
  result.value = null
  pick(id)
})

// 폴더 선택 시 안의 글 제목을 불러와 미리보기 (연동 시 서버가 폴더 항목을 바로 내려줌)
async function pick(id) {
  folderId.value = id
  items.value = []
  const f = folders.byId(id)
  if (!f) return
  name.value = f.name
  desc.value = ''
  loading.value = true
  const rows = await Promise.allSettled(folders.idsOf(id).map((pid) => fetchPost(pid)))
  items.value = rows.filter((r) => r.status === 'fulfilled').map((r) => r.value.data).filter(Boolean).map((p) => ({
    postId: p.id, title: p.aiTitle || (p.content ?? '').split('\n')[0].slice(0, 60) || '(제목 없음)', category: p.category ?? '기타',
  }))
  loading.value = false
}

const close = () => (store.pushFolderId = undefined)
function submit() {
  if (!folder.value || !items.value.length || !name.value.trim()) return
  phase.value = 'pushing'
  setTimeout(() => {
    result.value = store.push({ name: name.value.trim(), color: folder.value.color, items: items.value, desc: desc.value.trim(), visibility: visibility.value, mode: mode.value })
    phase.value = 'done'
  }, 2400)
}
function goRepo() {
  const r = result.value
  close()
  router.push(`/community/drive/${encodeURIComponent(r.owner)}/${encodeURIComponent(r.name)}`)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="pu-overlay" @click.self="phase !== 'pushing' && close()">
      <div class="pu" role="dialog" aria-modal="true">
        <template v-if="phase === 'form'">
          <div class="pu-head"><b>↑ 공유 드라이브에 Push</b><button aria-label="닫기" @click="close">✕</button></div>
          <p class="pu-sub">{{ auth.user?.name }} 님의 폴더를 레포로 올려 다른 교육생이 star·clone 할 수 있게 해요</p>

          <div class="pu-step">① 올릴 폴더</div>
          <div v-if="!folders.folders.length" class="pu-empty">아직 내 폴더가 없어요. 피드의 AI 추천 탭에서 폴더를 만들고 글을 담아 보세요.</div>
          <div class="pu-folders">
            <button v-for="f in folders.folders" :key="f.id" class="pu-folder" :class="{ on: folderId === f.id }" @click="pick(f.id)">
              <FolderIcon :color="f.color" :size="20" /> {{ f.name }} <span>{{ folders.count(f.id) }}</span>
            </button>
          </div>

          <template v-if="folder">
            <div class="pu-step">② 레포 정보</div>
            <label class="pu-label">레포 이름<input v-model="name" maxlength="20" /></label>
            <label class="pu-label">설명 (선택)<input v-model="desc" maxlength="80" placeholder="이 레포는 무엇을 모은 건가요?" /></label>
            <div class="pu-row">
              <label class="pu-label">공개 범위
                <select v-model="visibility"><option>Public</option><option value="링크만">링크만</option><option>Private</option></select>
              </label>
              <label class="pu-label">기여 방식
                <select v-model="mode"><option value="pr">승인 필요 (PR)</option><option value="open">바로 반영</option></select>
              </label>
            </div>
            <div class="pu-step">③ 포함될 글 <span class="pu-n">{{ loading ? '' : items.length + '개' }}</span></div>
            <div class="pu-prev">
              <p v-if="loading" class="pu-empty">불러오는 중…</p>
              <p v-else-if="!items.length" class="pu-empty">이 폴더에 담긴 글이 없어요. 글을 담은 뒤 push해 주세요.</p>
              <div v-for="i in items" :key="i.postId" class="pu-item">📄 <span>{{ i.title }}</span><em>{{ i.category }}</em></div>
            </div>
          </template>

          <div class="pu-foot">
            <button class="pu-cancel" @click="close">취소</button>
            <button class="pu-ok" :disabled="!folder || !items.length || !name.trim()" @click="submit">{{ exists ? '업데이트 push' : 'Push' }}</button>
          </div>
        </template>

        <div v-else-if="phase === 'pushing'" class="pu-center">
          <SaveLoader :width="72" :color="folder?.color" />
          <p>'{{ name }}' 공유 드라이브에 push하는 중…</p>
        </div>

        <div v-else class="pu-center">
          <div class="pu-ok-ic">✓</div>
          <p><b>'{{ result.name }}'</b> 레포가 {{ exists ? '업데이트' : '생성' }}됐어요</p>
          <div class="pu-foot"><button class="pu-cancel" @click="close">닫기</button><button class="pu-ok" @click="goRepo">레포 보러가기 →</button></div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pu-overlay { position: fixed; inset: 0; z-index: 1200; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(26, 26, 46, 0.45); }
.pu { width: 100%; max-width: 520px; max-height: 90vh; overflow-y: auto; padding: 20px; background: #fff; border-radius: 12px; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25); }
.pu-head { display: flex; align-items: center; justify-content: space-between; font-size: 16px; }
.pu-head button { border: none; background: none; font-size: 16px; cursor: pointer; }
.pu-sub { margin: 4px 0 14px; font-size: 12px; color: #636e72; }
.pu-step { margin: 14px 0 8px; font-size: 13px; font-weight: 700; color: #4a3f8f; }
.pu-n { margin-left: 4px; font-weight: 500; color: #636e72; }
.pu-folders { display: flex; flex-wrap: wrap; gap: 8px; }
.pu-folder { display: inline-flex; align-items: center; gap: 6px; padding: 7px 12px; border: 1px solid #d8d8e4; border-radius: 10px; background: #fff; font-size: 13px; cursor: pointer; }
.pu-folder span { color: #8a8fa0; font-size: 12px; }
.pu-folder.on { border-color: #6c5ce7; background: #f3f0ff; }
.pu-label { display: flex; flex-direction: column; gap: 4px; flex: 1; margin-bottom: 10px; font-size: 12px; color: #636e72; }
.pu-label input, .pu-label select { padding: 8px 10px; border: 1px solid #d8d8e4; border-radius: 8px; font-size: 14px; outline: none; background: #fff; }
.pu-label input:focus { border-color: #6c5ce7; }
.pu-row { display: flex; gap: 10px; }
.pu-prev { max-height: 150px; overflow-y: auto; border: 1px solid #eaeef2; border-radius: 8px; }
.pu-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-top: 1px solid #eaeef2; font-size: 13px; }
.pu-item:first-child { border-top: none; }
.pu-item span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pu-item em { padding: 1px 8px; border-radius: 10px; background: #efeaff; color: #4a3f8f; font-size: 11px; font-style: normal; }
.pu-empty { padding: 14px; font-size: 13px; color: #8a8fa0; text-align: center; }
.pu-foot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.pu-cancel { padding: 8px 14px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; font-size: 13px; cursor: pointer; }
.pu-ok { padding: 8px 18px; border: none; border-radius: 8px; background: #4a3f8f; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.pu-ok:disabled { opacity: 0.4; cursor: default; }
.pu-center { display: flex; flex-direction: column; align-items: center; padding: 30px 0 10px; font-size: 14px; color: #1a1a2e; }
.pu-ok-ic { width: 44px; height: 44px; margin-bottom: 8px; border-radius: 50%; background: #6c5ce7; color: #fff; font-size: 24px; line-height: 44px; text-align: center; }
@media (max-width: 520px) { .pu-row { flex-direction: column; gap: 0; } }
</style>
