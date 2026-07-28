<script setup>
// 카테고리별 필터링 칩
import { onMounted } from 'vue'
import { usePostsStore } from '../stores/posts'
import { CATEGORIES } from '../constants/categories'

const postsStore = usePostsStore()

onMounted(() => {
  postsStore.loadCategoryCounts()
})

function select(value) {
  postsStore.setCategory(value, null)
}
</script>

<template>
  <nav class="category-filter">
    <div class="chip" :class="{ active: !postsStore.category }" @click="select(null)">
      전체 ({{ postsStore.totalPostCount }})
    </div>
    <div
      v-for="cat in CATEGORIES"
      :key="cat.value"
      class="chip"
      :class="{ active: postsStore.category === cat.value }"
      @click="select(cat.value)"
    >
      {{ cat.shortLabel }} ({{ postsStore.categoryCount(cat.value) }})
    </div>
  </nav>
</template>

<style scoped>
.category-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.chip {
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.1);
  cursor: pointer;
}

.chip.active {
  background: #4a3f8f;
  color: #ffffff;
  border-color: #4a3f8f;
}
</style>
