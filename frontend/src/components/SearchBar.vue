<script setup>
// 게시글 검색 입력창
const emit = defineEmits(['search'])
</script>

<template>
  <div class="search-bar">
    <input type="text" placeholder="검색어를 입력하세요" @keyup.enter="emit('search', $event.target.value)" />
  </div>
</template>
