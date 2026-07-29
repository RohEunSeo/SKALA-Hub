<script setup>
// 기간 필터 (전체/오늘/이번주/이번달/월별) - 월별 클릭 시 드롭다운으로 교육 기간(7~12월)만 선택
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePostsStore } from '../stores/posts'

const postsStore = usePostsStore()

const EDUCATION_MONTHS = [7, 8, 9, 10, 11, 12]
const currentYear = new Date().getFullYear()
const showMonthDropdown = ref(false)

const isMonthly = computed(() => /^\d{4}-\d{2}$/.test(postsStore.date ?? ''))

function monthValue(month) {
  return `${currentYear}-${String(month).padStart(2, '0')}`
}

function select(value) {
  showMonthDropdown.value = false
  postsStore.setDate(postsStore.date === value ? null : value)
}

function selectMonth(month) {
  select(monthValue(month))
}

function toggleMonthDropdown() {
  showMonthDropdown.value = !showMonthDropdown.value
}

function closeDropdownOnOutsideClick(event) {
  if (!event.target.closest('.month-dropdown-wrapper')) {
    showMonthDropdown.value = false
  }
}

onMounted(() => document.addEventListener('click', closeDropdownOnOutsideClick))
onUnmounted(() => document.removeEventListener('click', closeDropdownOnOutsideClick))
</script>

<template>
  <div class="date-filter">
    <span class="label">📅 기간</span>
    <div class="pill" :class="{ active: !postsStore.date }" @click="postsStore.setDate(null)">전체</div>
    <div class="pill" :class="{ active: postsStore.date === 'today' }" @click="select('today')">오늘</div>
    <div class="pill" :class="{ active: postsStore.date === 'week' }" @click="select('week')">이번 주</div>
    <div class="pill" :class="{ active: postsStore.date === 'month' }" @click="select('month')">이번 달</div>

    <div class="month-dropdown-wrapper">
      <div class="pill" :class="{ active: isMonthly }" @click.stop="toggleMonthDropdown">월별 ▾</div>
      <div v-if="showMonthDropdown" class="month-dropdown">
        <div
          v-for="month in EDUCATION_MONTHS"
          :key="month"
          class="month-option"
          :class="{ active: postsStore.date === monthValue(month) }"
          @click.stop="selectMonth(month)"
        >
          {{ month }}월
        </div>
      </div>
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

.month-dropdown-wrapper {
  position: relative;
}

.month-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(26, 26, 46, 0.12);
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  z-index: 10;
  min-width: 90px;
}

.month-option {
  padding: 7px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

.month-option:hover {
  background: #f1eefc;
}

.month-option.active {
  background: #4a3f8f;
  color: #ffffff;
}
</style>
