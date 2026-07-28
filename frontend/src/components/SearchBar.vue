<script setup>
// 게시글 검색 (키워드 + 작성자) - 입력하는 대로 실시간(디바운스) 검색
import { ref, watch } from 'vue'

const emit = defineEmits(['search'])
const keyword = ref('')
const author = ref('')

let debounceTimer = null

function submit() {
  clearTimeout(debounceTimer)
  emit('search', { keyword: keyword.value, author: author.value })
}

watch([keyword, author], () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(submit, 300)
})
</script>

<template>
  <div class="search-bar">
    <input
      v-model="keyword"
      type="text"
      placeholder="예: SQLD, 1반, 취업 후기... 무엇이든 검색해보세요"
      class="search-input"
      @keyup.enter="submit"
    />
    <input
      v-model="author"
      type="text"
      placeholder="작성자 이름"
      class="author-input"
      @keyup.enter="submit"
    />
    <div class="search-btn" @click="submit">🔍 검색</div>
  </div>
</template>

<style scoped>
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-input {
  flex: 1;
}

.author-input {
  width: 160px;
}

.search-input,
.author-input {
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  font-size: 14px;
  font-family: inherit;
  background: #ffffff;
}

.search-btn {
  padding: 12px 18px;
  border-radius: 12px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  background: #ffffff;
  font-size: 13px;
  color: #1a1a2e;
  cursor: pointer;
  white-space: nowrap;
}

.search-btn:hover {
  border-color: #4a3f8f;
  color: #4a3f8f;
}
</style>
