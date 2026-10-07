<script setup>
// 공유 드라이브 목록의 레포 카드 - GitHub 레포 목록 느낌 (owner / name, 설명, 토픽, ★ ⑂ 수)
import { useCommunityStore } from '../../stores/community'
import FolderIcon from '../FolderIcon.vue'

defineProps({ repo: { type: Object, required: true } })
const store = useCommunityStore()
</script>

<template>
  <RouterLink :to="`/community/drive/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.name)}`" class="rc">
    <div class="rc-head">
      <FolderIcon :color="repo.color" :size="18" />
      <span class="rc-name"><span class="rc-owner">{{ repo.owner }}</span> / {{ repo.name }}</span>
      <span v-if="repo.official" class="rc-official">공식</span>
      <span class="rc-vis">{{ repo.visibility }}</span>
    </div>
    <p class="rc-desc">{{ repo.desc }}</p>
    <div class="rc-topics"><span v-for="t in repo.topics" :key="t" class="rc-topic">{{ t }}</span></div>
    <div class="rc-meta">
      <span>★ {{ store.starCount(repo) }}</span>
      <span>⑂ {{ repo.forks }}</span>
      <span>📄 {{ repo.files.length }}+</span>
      <span class="rc-ago">{{ repo.updated }} 업데이트</span>
    </div>
  </RouterLink>
</template>

<style scoped>
.rc { display: block; padding: 16px; background: #fff; border: 1px solid #d8d8e4; border-radius: 8px; text-decoration: none; color: #1a1a2e; transition: border-color 0.15s, box-shadow 0.15s; }
.rc:hover { border-color: #6c5ce7; box-shadow: 0 2px 10px rgba(108, 92, 231, 0.12); }
.rc-head { display: flex; align-items: center; gap: 8px; }
.rc-name { font-size: 15px; font-weight: 600; color: #4a3f8f; }
.rc-owner { font-weight: 500; }
.rc-official { padding: 1px 8px; border-radius: 10px; background: #4a3f8f; color: #fff; font-size: 11px; font-weight: 600; }
.rc-vis { margin-left: auto; padding: 0 8px; border: 1px solid #d8d8e4; border-radius: 10px; font-size: 11px; color: #636e72; }
.rc-desc { margin: 8px 0 10px; font-size: 13px; line-height: 1.5; color: #636e72; min-height: 39px; }
.rc-topics { display: flex; flex-wrap: wrap; gap: 6px; }
.rc-topic { padding: 2px 10px; border-radius: 12px; background: #efeaff; color: #4a3f8f; font-size: 12px; font-weight: 500; }
.rc-meta { display: flex; gap: 14px; margin-top: 12px; font-size: 12px; color: #636e72; }
.rc-ago { margin-left: auto; }
</style>
