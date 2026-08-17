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
  window.scrollTo({ top: 0, behavior: 'auto' })
}
</script>

<template>
  <nav class="category-filter">
    <div class="chip" :class="{ active: !postsStore.category }" @click="select(null)">
      전체
      <span class="count">({{ postsStore.hasLink ? postsStore.totalLinkCount : postsStore.totalPostCount }})</span>
    </div>
    <div
      v-for="cat in CATEGORIES"
      :key="cat.value"
      class="chip"
      :class="{ active: postsStore.category === cat.value }"
      @click="select(cat.value)"
    >
      {{ cat.shortLabel }}
      <span class="count">
        ({{ postsStore.hasLink ? postsStore.linkCategoryCount(cat.value) : postsStore.categoryCount(cat.value) }})
      </span>
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

/* 모바일은 칩 7개가 개별 테두리로 나열되면 3줄로 줄바꿈되고 마지막 줄에 하나만 남아 어색해 보임 -
   다른 필터들(edu-category-filter/campus-filter/date-filter)과 같은 "박스" 스타일로 감싸고,
   칩 패딩을 줄이고 카운트 숫자만 작게 축소해서 2줄에 가깝게 정리한다 */
@media (max-width: 768px) {
  .category-filter {
    gap: 5px;
    background: #ffffff;
    border: 1px solid rgba(26, 26, 46, 0.08);
    border-radius: 12px;
    padding: 6px;
    width: fit-content;
  }

  .chip {
    padding: 6px 10px;
    border-radius: 9px;
    border-color: transparent;
    background: transparent;
  }

  .chip.active {
    background: #4a3f8f;
    color: #ffffff;
    border-color: transparent;
  }

  /* 라벨은 그대로 두고 카운트만 축소 - 정보는 남기되 차지하는 폭을 줄여서 2줄에 들어갈 여유를 만듦.
     색은 지정하지 않고 상속시켜서 active/비active 글씨색을 그대로 따라가게 함 */
  .chip .count {
    font-size: 10px;
    opacity: 0.75;
    margin-left: 1px;
  }
}
</style>
