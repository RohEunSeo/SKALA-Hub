<script setup>
// 정렬 드롭다운 (최신순/인기순/오래된순) - 하위카테고리 선택 후 부가적으로 쓰는 옵션이라 작은 드롭다운 형태로 제공
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePostsStore } from '../stores/posts'

const postsStore = usePostsStore()
const isOpen = ref(false)

const SORTS = [
  { value: 'latest', label: '최신순' },
  { value: 'popular', label: '인기순' },
  { value: 'oldest', label: '오래된순' },
  { value: 'saved', label: '저장순' },
]

const currentLabel = computed(() => SORTS.find((opt) => opt.value === postsStore.sort)?.label ?? '최신순')

function toggleOpen() {
  isOpen.value = !isOpen.value
}

function select(value) {
  isOpen.value = false
  postsStore.setSort(value)
}

function closeDropdownOnOutsideClick(event) {
  if (!event.target.closest('.sort-dropdown-wrapper')) {
    isOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', closeDropdownOnOutsideClick))
onUnmounted(() => document.removeEventListener('click', closeDropdownOnOutsideClick))
</script>

<template>
  <div class="sort-dropdown-wrapper">
    <div class="sort-trigger" @click.stop="toggleOpen">
      정렬: <strong>{{ currentLabel }}</strong> <span class="chevron">▾</span>
    </div>
    <div v-if="isOpen" class="sort-menu">
      <div
        v-for="opt in SORTS"
        :key="opt.value"
        class="sort-option"
        :class="{ active: postsStore.sort === opt.value }"
        @click="select(opt.value)"
      >
        {{ opt.label }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.sort-dropdown-wrapper {
  position: relative;
  flex-shrink: 0;
}

.sort-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 9px 14px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  font-size: 13px;
  color: #636e72;
  cursor: pointer;
  white-space: nowrap;
}

.sort-trigger strong {
  color: #1a1a2e;
}

.chevron {
  font-size: 10px;
  color: #636e72;
}

.sort-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(26, 26, 46, 0.12);
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  z-index: 10;
  min-width: 110px;
}

.sort-option {
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

.sort-option:hover {
  background: #f1eefc;
}

.sort-option.active {
  background: #f1eefc;
  color: #4a3f8f;
}
</style>
