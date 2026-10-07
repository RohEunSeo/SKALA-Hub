<script setup>
// 내 글 고르기 모달 - 레포에 글 추가(바로) / Pull request 제안 / 게시판 글 첨부에서 공용
// 내가 쓴 글을 불러오고, 하나도 없으면(또는 조회 실패) 샘플 글로 대체해서 흐름을 확인할 수 있게 함
import { computed, onMounted, ref } from 'vue'
import { fetchPosts } from '../../api/posts'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  mode: { type: String, default: 'direct' }, // direct(바로 추가) | pr(제안) | pick(첨부용 1개 선택)
  repoName: { type: String, default: '' },
})
const emit = defineEmits(['close', 'submit'])
const auth = useAuthStore()

const SAMPLE = ['SQLD 3과목 실전 문제 풀이', 'RAG 청킹 전략 비교 정리', 'Git 협업 브랜치 전략 가이드', 'Vue3 면접 질문 모음'].map((title, i) => ({
  postId: null, key: `s${i}`, title, category: ['자격증·취업', '학습 자료', '개발 툴·환경', '자격증·취업'][i],
}))
const posts = ref([])
const loading = ref(true)
const picked = ref([])
const title = ref('')

onMounted(async () => {
  const name = auth.user?.name
  try {
    const rows = name ? ((await fetchPosts({ author: name, page: 0, size: 30 })).data?.content ?? []) : []
    posts.value = rows.filter((p) => p.userName?.includes(name)).map((p) => ({
      postId: p.id, key: `p${p.id}`, title: p.aiTitle || (p.content ?? '').split('\n')[0].slice(0, 60) || '(제목 없음)', category: p.category ?? '기타',
    }))
  } catch { /* 조회 실패 시 샘플 사용 */ }
  if (!posts.value.length) posts.value = SAMPLE
  loading.value = false
})

const toggle = (p) => {
  if (props.mode === 'pick') return (picked.value = [p])
  picked.value = picked.value.includes(p) ? picked.value.filter((x) => x !== p) : [...picked.value, p]
}
const heading = computed(() => ({ direct: '내 글 바로 추가', pr: '글 추가 제안 (Pull request)', pick: '첨부할 글 선택' })[props.mode])
const label = computed(() => ({ direct: '추가하기', pr: 'Pull request 만들기', pick: '첨부' })[props.mode])
const canSubmit = computed(() => picked.value.length && (props.mode !== 'pr' || title.value.trim()))
function submit() {
  emit('submit', { title: title.value.trim() || `${picked.value[0].title} 외 ${picked.value.length - 1}개 추가`, items: picked.value })
}
</script>

<template>
  <Teleport to="body">
    <div class="am-overlay" @click.self="emit('close')">
      <div class="am" role="dialog" aria-modal="true">
        <div class="am-head"><b>{{ heading }}</b><button aria-label="닫기" @click="emit('close')">✕</button></div>
        <p v-if="repoName" class="am-sub">{{ repoName }}</p>
        <input v-if="mode === 'pr'" v-model="title" class="am-title" maxlength="60" placeholder="제안 제목 (예: SQLD 기출 해설 3개 추가)" />
        <div class="am-list">
          <div v-if="loading" class="am-empty">내 글을 불러오는 중…</div>
          <label v-for="p in posts" :key="p.key" class="am-row" :class="{ on: picked.includes(p) }">
            <input :type="mode === 'pick' ? 'radio' : 'checkbox'" :checked="picked.includes(p)" @change="toggle(p)" />
            <span class="am-t">{{ p.title }}</span><span class="am-c">{{ p.category }}</span>
          </label>
        </div>
        <div class="am-foot">
          <span class="am-n">{{ picked.length }}개 선택</span>
          <button class="am-cancel" @click="emit('close')">취소</button>
          <button class="am-ok" :disabled="!canSubmit" @click="submit">{{ label }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.am-overlay { position: fixed; inset: 0; z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(26, 26, 46, 0.45); }
.am { width: 100%; max-width: 520px; max-height: 84vh; display: flex; flex-direction: column; padding: 18px; background: #fff; border-radius: 12px; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25); }
.am-head { display: flex; align-items: center; justify-content: space-between; font-size: 16px; }
.am-head button { border: none; background: none; font-size: 16px; cursor: pointer; }
.am-sub { margin: 4px 0 10px; font-size: 12px; color: #636e72; }
.am-title { margin: 8px 0; padding: 9px 12px; border: 1px solid #d8d8e4; border-radius: 8px; font-size: 14px; outline: none; }
.am-title:focus { border-color: #6c5ce7; }
.am-list { flex: 1; overflow-y: auto; margin: 8px 0; border: 1px solid #eaeef2; border-radius: 8px; }
.am-row { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-top: 1px solid #eaeef2; font-size: 13px; cursor: pointer; }
.am-row:first-child { border-top: none; }
.am-row.on { background: #f3f0ff; }
.am-t { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.am-c { padding: 1px 8px; border-radius: 10px; background: #efeaff; color: #4a3f8f; font-size: 11px; white-space: nowrap; }
.am-empty { padding: 30px 0; text-align: center; font-size: 13px; color: #636e72; }
.am-foot { display: flex; align-items: center; gap: 8px; }
.am-n { margin-right: auto; font-size: 12px; color: #636e72; }
.am-cancel { padding: 7px 14px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; font-size: 13px; cursor: pointer; }
.am-ok { padding: 7px 16px; border: none; border-radius: 8px; background: #4a3f8f; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.am-ok:disabled { opacity: 0.4; cursor: default; }
</style>
