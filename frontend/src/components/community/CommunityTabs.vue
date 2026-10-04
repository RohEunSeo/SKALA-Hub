<script setup>
// 커뮤니티 상단 세그먼트 탭 (게시판 | 드라이브) - 같은 탭 안에서 화면만 분리
defineProps({ active: { type: String, required: true } })
</script>

<template>
  <div class="ct">
    <RouterLink to="/community/board" class="ct-item" :class="{ on: active === 'board' }">💬 게시판</RouterLink>
    <RouterLink to="/community/drive" class="ct-item" :class="{ on: active === 'drive' }">🗂 공유 드라이브</RouterLink>
  </div>
</template>

<style scoped>
.ct { display: inline-flex; padding: 3px; gap: 2px; background: #efeff6; border-radius: 10px; margin-bottom: 20px; }
.ct-item { padding: 7px 16px; border-radius: 8px; font-size: 14px; font-weight: 600; color: #636e72; text-decoration: none; }
.ct-item.on { background: #fff; color: #4a3f8f; box-shadow: 0 1px 3px rgba(74, 63, 143, 0.15); }
</style>
