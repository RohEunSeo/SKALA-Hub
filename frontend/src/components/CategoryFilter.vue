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
      <span class="label">전체</span
      ><span class="count">({{ postsStore.hasLink ? postsStore.totalLinkCount : postsStore.totalPostCount }})</span>
    </div>
    <div
      v-for="cat in CATEGORIES"
      :key="cat.value"
      class="chip"
      :class="{ active: postsStore.category === cat.value }"
      @click="select(cat.value)"
    >
      <span class="label">{{ cat.shortLabel }}</span
      ><span class="count"
        >({{ postsStore.hasLink ? postsStore.linkCategoryCount(cat.value) : postsStore.categoryCount(cat.value) }})</span
      >
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
   칩 패딩·간격을 줄이고 폰트를 축소해서 카운트를 유지한 채로 2줄에 들어가게 한다.
   라벨과 카운트 사이 공백은 템플릿에서 태그를 붙여써서 없앴음(공백 하나도 여러 칩이 쌓이면 무시 못 할 폭).
   폰트를 13px/11px까지 키워봤더니 실기기에서 4+3이 3+3+1로 무너지는 걸 확인해서, 2줄이 확인된
   12px/10px로 되돌리고 간격만 더 좁혀서 여유를 추가로 확보한다 */
@media (max-width: 768px) {
  .category-filter {
    gap: 4px 6px;
    background: #ffffff;
    border: 1px solid rgba(26, 26, 46, 0.08);
    border-radius: 12px;
    padding: 7px 9px;
    width: fit-content;
  }

  .chip {
    display: flex;
    align-items: baseline;
    gap: 1px;
    padding: 6px 7px;
    border-radius: 9px;
    border-color: transparent;
    background: transparent;
  }

  .chip.active {
    background: #4a3f8f;
    color: #ffffff;
    border-color: transparent;
  }

  .chip .label {
    font-size: 12px;
  }

  /* 카운트는 라벨보다 한 단계 더 작게 - 정보는 유지하되 차지하는 폭을 최대한 줄임 */
  .chip .count {
    font-size: 10px;
    opacity: 0.8;
  }
}
</style>
