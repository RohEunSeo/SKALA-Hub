<script setup>
// 카테고리별 필터링 탭
defineProps({
  categories: { type: Array, default: () => [] },
})
</script>

<template>
  <nav class="category-filter">
    <button v-for="category in categories" :key="category">{{ category }}</button>
  </nav>
</template>
