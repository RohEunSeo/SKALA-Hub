<script setup>
// 레포 안의 글 보기 모달 - 레포 화면을 떠나지 않고 실제 글(PostCard)을 읽고, ‹ › 로 같은 폴더의 다음 글로 이동
// 실제 글 ID(postId)가 있으면 서버에서 불러오고, 목업 글(ID 없음/조회 실패)은 요약 카드로 대체
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PostCard from '../PostCard.vue'
import { fetchPost } from '../../api/posts'

const props = defineProps({
  items: { type: Array, required: true }, // 이동 가능한 글 목록 (현재 폴더)
  start: { type: Number, default: 0 },
  path: { type: String, default: '' }, // 상단 경로 표시 (레포 / 폴더)
})
const emit = defineEmits(['close'])
const router = useRouter()

const idx = ref(props.start)
const item = computed(() => props.items[idx.value])
const post = ref(null)
const state = ref('loading') // loading | ok | fallback | deleted
const cache = {}

async function load() {
  const it = item.value
  if (!it?.postId) return ((post.value = null), (state.value = 'fallback'))
  if (cache[it.postId]) return ((post.value = cache[it.postId]), (state.value = 'ok'))
  state.value = 'loading'
  try {
    const { data } = await fetchPost(it.postId)
    if (data?.isDeleted) return (state.value = 'deleted')
    cache[it.postId] = post.value = data
    state.value = 'ok'
  } catch (e) {
    state.value = e.response?.status === 404 ? 'deleted' : 'fallback'
  }
}
watch(idx, load, { immediate: true })

const go = (d) => (idx.value = Math.min(props.items.length - 1, Math.max(0, idx.value + d)))
const onKey = (e) => {
  if (e.key === 'Escape') emit('close')
  else if (e.key === 'ArrowLeft') go(-1)
  else if (e.key === 'ArrowRight') go(1)
}
onMounted(() => { window.addEventListener('keydown', onKey); document.body.style.overflow = 'hidden' })
onBeforeUnmount(() => { window.removeEventListener('keydown', onKey); document.body.style.overflow = '' })
</script>

<template>
  <Teleport to="body">
    <div class="pm-overlay" @click.self="emit('close')">
      <div class="pm-panel" role="dialog" aria-modal="true">
        <div class="pm-top">
          <span class="pm-path">{{ path }}</span>
          <div class="pm-nav">
            <button :disabled="idx === 0" aria-label="이전 글" @click="go(-1)">‹</button>
            <span>{{ idx + 1 }} / {{ items.length }}</span>
            <button :disabled="idx === items.length - 1" aria-label="다음 글" @click="go(1)">›</button>
          </div>
          <button class="pm-close" aria-label="닫기" @click="emit('close')">✕</button>
        </div>
        <div class="pm-body">
          <div v-if="state === 'loading'" class="pm-msg">글을 불러오는 중…</div>
          <div v-else-if="state === 'deleted'" class="pm-msg">삭제되었거나 볼 수 없는 글이에요</div>
          <PostCard v-else-if="state === 'ok'" :key="post.id" :post="post" :link-to-detail="false" />
          <div v-else class="pm-fallback">
            <h3>{{ item.title }}</h3>
            <div class="pm-meta">{{ item.cat }} · {{ item.ago }} 저장</div>
            <p>{{ item.summary }}</p>
            <div class="pm-note">목업 글이라 요약만 보여요. 실제 push한 글은 원문 전체가 이 자리에 열려요.</div>
          </div>
        </div>
        <div v-if="state === 'ok'" class="pm-foot">
          <button @click="router.push(`/posts/${item.postId}`)">원문 페이지 열기 ↗</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pm-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(26, 26, 46, 0.45); }
.pm-panel { display: flex; flex-direction: column; width: 100%; max-width: 720px; max-height: 88vh; background: #fafafa; border-radius: 12px; overflow: hidden; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25); }
.pm-top { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: #fff; border-bottom: 1px solid #eaeef2; }
.pm-path { flex: 1; min-width: 0; font-size: 13px; color: #636e72; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pm-nav { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #636e72; }
.pm-nav button, .pm-close { width: 28px; height: 28px; border: 1px solid #d8d8e4; border-radius: 6px; background: #fff; font-size: 16px; line-height: 1; cursor: pointer; }
.pm-nav button:disabled { opacity: 0.35; cursor: default; }
.pm-close { border: none; background: none; font-size: 16px; }
.pm-body { flex: 1; overflow-y: auto; padding: 16px; }
.pm-msg { padding: 60px 0; text-align: center; color: #636e72; font-size: 14px; }
.pm-fallback { padding: 20px; background: #fff; border: 1px solid #eaeef2; border-radius: 10px; }
.pm-fallback h3 { margin: 0 0 6px; font-size: 16px; }
.pm-meta { font-size: 12px; color: #636e72; }
.pm-fallback p { font-size: 14px; line-height: 1.7; }
.pm-note { margin-top: 12px; padding: 8px 12px; border-radius: 8px; background: #f3f0ff; font-size: 12px; color: #4a3f8f; }
.pm-foot { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 16px; background: #fff; border-top: 1px solid #eaeef2; }
.pm-foot button { padding: 6px 14px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; font-size: 13px; cursor: pointer; }
.pm-foot button:hover { border-color: #6c5ce7; color: #4a3f8f; }
</style>
