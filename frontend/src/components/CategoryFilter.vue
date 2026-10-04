<script setup>
// 카테고리별 필터링 칩
import { computed, onMounted } from 'vue'
import { usePostsStore } from '../stores/posts'
import { useFoldersStore } from '../stores/folders'
import { CATEGORIES } from '../constants/categories'

// counts를 주면 스토어 대신 넘겨받은 목록 기준으로 동작 (AI 추천 탭용 - { all, [카테고리 value]: 개수 }, 상위 카테고리는 항상 전부 표시)
const props = defineProps({ counts: { type: Object, default: null }, selected: { type: String, default: null } })
const emit = defineEmits(['select'])
const postsStore = usePostsStore()
const foldersStore = useFoldersStore()
const local = computed(() => !!props.counts)

// 내 폴더를 열어 둔 동안은 카테고리가 실제로 적용되지 않는다.
// 그런데도 "전체"가 켜져 보이면 화면이 거짓말을 하게 되므로, 그동안은 아무것도 켜지 않고 흐리게 둔다.
// (칩을 누르면 폴더가 풀리고 그 카테고리로 넘어간다 - FeedView의 category watch)
const dimmed = computed(() => !local.value && !!foldersStore.selected)

const isActive = (value) => {
  if (local.value) return (props.selected ?? null) === value
  if (dimmed.value) return false
  return (postsStore.category ?? null) === value
}
const countOf = (value) => {
  if (local.value) return props.counts[value ?? 'all'] ?? 0
  if (value === null) return postsStore.hasLink ? postsStore.totalLinkCount : postsStore.totalPostCount
  return postsStore.hasLink ? postsStore.linkCategoryCount(value) : postsStore.categoryCount(value)
}

onMounted(() => {
  if (!local.value) postsStore.loadCategoryCounts()
})

function select(value) {
  if (local.value) return emit('select', value)
  postsStore.setCategory(value, null)
  window.scrollTo({ top: 0, behavior: 'auto' })
}
</script>

<template>
  <nav class="category-filter" :class="{ dimmed }">
    <div class="chip" :class="{ active: isActive(null) }" @click="select(null)">
      <span class="label">전체</span><span class="count">({{ countOf(null) }})</span>
    </div>
    <div v-for="cat in CATEGORIES" :key="cat.value" class="chip" :class="{ active: isActive(cat.value) }" @click="select(cat.value)">
      <span class="label">{{ cat.shortLabel }}</span><span class="count">({{ countOf(cat.value) }})</span>
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

.category-filter.dimmed .chip {
  opacity: 0.42;   /* 폴더를 보는 중 - 지금은 안 쓰이는 필터라는 표시 */
}

.category-filter.dimmed .chip:hover {
  opacity: 1;      /* 누르면 폴더가 풀리고 이 카테고리로 간다는 힌트 */
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
  /* 아래 층/기간 박스와 폭을 맞추기 위해 fit-content 대신 100%(검색창과 같은 폭)로 통일 */
  .category-filter {
    gap: 4px 6px;
    background: #ffffff;
    border: 1px solid rgba(26, 26, 46, 0.08);
    border-radius: 12px;
    padding: 7px 9px;
    width: 100%;
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

  .category-filter.dimmed .chip {
  opacity: 0.42;   /* 폴더를 보는 중 - 지금은 안 쓰이는 필터라는 표시 */
}

.category-filter.dimmed .chip:hover {
  opacity: 1;      /* 누르면 폴더가 풀리고 이 카테고리로 간다는 힌트 */
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
