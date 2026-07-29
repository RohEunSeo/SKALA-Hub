<script setup>
// 기간 필터 (오늘/이번주/이번달/월별)
import { ref, computed } from 'vue'
import { usePostsStore } from '../stores/posts'

const postsStore = usePostsStore()
const monthInput = ref('')

const isMonthly = computed(() => /^\d{4}-\d{2}$/.test(postsStore.date ?? ''))

function select(value) {
  monthInput.value = ''
  postsStore.setDate(postsStore.date === value ? null : value)
}

function onMonthChange(event) {
  monthInput.value = event.target.value
  if (monthInput.value) {
    postsStore.setDate(monthInput.value)
  }
}
</script>

<template>
  <div class="date-filter">
    <span class="label">📅 기간</span>
    <div class="pill" :class="{ active: !postsStore.date }" @click="postsStore.setDate(null)">전체</div>
    <div class="pill" :class="{ active: postsStore.date === 'today' }" @click="select('today')">오늘</div>
    <div class="pill" :class="{ active: postsStore.date === 'week' }" @click="select('week')">이번 주</div>
    <div class="pill" :class="{ active: postsStore.date === 'month' }" @click="select('month')">이번 달</div>
    <label class="pill month-pill" :class="{ active: isMonthly }">
      월별 ▾
      <input type="month" :value="monthInput" @change="onMonthChange" />
    </label>
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

.month-pill {
  display: flex;
  align-items: center;
}

.month-pill input[type='month'] {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
</style>
