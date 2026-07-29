<script setup>
// 기간 필터 (오늘/이번주/월별) - 교육 기간이 7~12월이라 월별은 그 6개월만 고정 제공
import { usePostsStore } from '../stores/posts'

const postsStore = usePostsStore()

const EDUCATION_MONTHS = [7, 8, 9, 10, 11, 12]
const currentYear = new Date().getFullYear()

function monthValue(month) {
  return `${currentYear}-${String(month).padStart(2, '0')}`
}

function select(value) {
  postsStore.setDate(postsStore.date === value ? null : value)
}
</script>

<template>
  <div class="date-filter">
    <span class="label">📅 기간</span>
    <div class="pill" :class="{ active: !postsStore.date }" @click="postsStore.setDate(null)">전체</div>
    <div class="pill" :class="{ active: postsStore.date === 'today' }" @click="select('today')">오늘</div>
    <div class="pill" :class="{ active: postsStore.date === 'week' }" @click="select('week')">이번 주</div>
    <div
      v-for="month in EDUCATION_MONTHS"
      :key="month"
      class="pill"
      :class="{ active: postsStore.date === monthValue(month) }"
      @click="select(monthValue(month))"
    >
      {{ month }}월
    </div>
  </div>
</template>

<style scoped>
.date-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 12px;
  padding: 6px;
  margin-bottom: 28px;
  width: fit-content;
}

.label {
  font-size: 12.5px;
  color: #636e72;
  padding: 0 6px 0 8px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.pill {
  padding: 7px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
}

.pill.active {
  background: #4a3f8f;
  color: #ffffff;
}
</style>
