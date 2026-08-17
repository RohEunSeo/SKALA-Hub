<script setup>
// 캠퍼스(4층/5층) 필터
import { usePostsStore } from '../stores/posts'

const postsStore = usePostsStore()

const CAMPUSES = [
  { value: null, label: '전체' },
  { value: '4층', label: '4층' },
  { value: '5층', label: '5층' },
]

function select(value) {
  postsStore.setCampus(value)
}
</script>

<template>
  <div class="campus-filter">
    <span class="label">🏢 층 : </span>
    <div
      v-for="opt in CAMPUSES"
      :key="opt.label"
      class="pill"
      :class="{ active: postsStore.campus === opt.value }"
      @click="select(opt.value)"
    >
      {{ opt.label }}
    </div>
  </div>
</template>

<style scoped>
.campus-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 12px;
  padding: 6px;
  width: fit-content;
}

.label {
  font-size: 13px;
  color: #636e72;
  padding: 0 2px 0 6px;
  font-weight: 600;
  white-space: nowrap;
}

.pill {
  padding: 6px 12px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

.pill.active {
  background: #f1eefc;
  color: #4a3f8f;
}

/* 옆에 나란히 붙는 기간 필터(DateFilter)와 패딩·폰트 축소 기준을 맞춰서 박스 높이가 항상 같게 함 */
@media (max-width: 1024px) {
  .label {
    font-size: 12px;
    padding: 0 2px 0 4px;
  }

  .pill {
    padding: 5px 9px;
    font-size: 12px;
  }
}
</style>
