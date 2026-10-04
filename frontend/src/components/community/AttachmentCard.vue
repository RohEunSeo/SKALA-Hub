<script setup>
// 게시판 글/댓글/우편에 붙는 첨부 카드 - 레포(드라이브) 또는 Hub 글
import { useRouter } from 'vue-router'
import { useCommunityStore } from '../../stores/community'
import FolderIcon from '../FolderIcon.vue'

const props = defineProps({ attach: { type: Object, required: true } })
const router = useRouter()
const store = useCommunityStore()
const repo = props.attach.kind === 'repo' ? store.repoById(props.attach.id) : null

function open() {
  if (repo) router.push(`/community/drive/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.name)}`)
  else if (props.attach.postId) router.push(`/posts/${props.attach.postId}`)
}
</script>

<template>
  <div v-if="repo" class="at" role="button" tabindex="0" @click.stop="open" @keydown.enter="open">
    <FolderIcon :color="repo.color" :size="20" />
    <div class="at-main">
      <b>{{ repo.owner }} / {{ repo.name }}</b>
      <span>{{ repo.desc }}</span>
      <small>★ {{ store.starCount(repo) }} · ⑂ {{ repo.forks }} · 글 {{ repo.files.length }}+</small>
    </div>
    <span class="at-go">레포 보기 ›</span>
  </div>
  <div v-else-if="attach.kind === 'post'" class="at" :class="{ plain: !attach.postId }" role="button" tabindex="0" @click.stop="open">
    <span class="at-ic">📄</span>
    <div class="at-main"><b>{{ attach.title }}</b><small>Hub 글</small></div>
    <span v-if="attach.postId" class="at-go">글 보기 ›</span>
  </div>
</template>

<style scoped>
.at { display: flex; align-items: center; gap: 12px; margin-top: 10px; padding: 10px 14px; border: 1px solid #d8d8e4; border-radius: 8px; background: #fff; cursor: pointer; text-align: left; }
.at.plain { cursor: default; }
.at:hover:not(.plain) { border-color: #6c5ce7; }
.at-ic { font-size: 18px; }
.at-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; font-size: 13px; }
.at-main b { color: #4a3f8f; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.at-main span { color: #636e72; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.at-main small { color: #8a8fa0; font-size: 11px; }
.at-go { font-size: 12px; color: #6c5ce7; white-space: nowrap; }
</style>
