<script setup>
// 커리큘럼 탭 "+ 게시글 추가" 모달 - 게시글 검색 → 선택 → 단계/하위카테고리 지정 → 추가
import { onMounted, onUnmounted, ref } from 'vue'
import { fetchPosts } from '../api/posts'
import { addCurriculumPost } from '../api/admin'
import { useToastStore } from '../stores/toast'
import CurriculumPicker from './CurriculumPicker.vue'

const emit = defineEmits(['close', 'added'])

const toastStore = useToastStore()
const keyword = ref('')
const results = ref([])
const searching = ref(false)
const selectedPost = ref(null)

async function search() {
  if (!keyword.value.trim()) {
    results.value = []
    return
  }
  searching.value = true
  try {
    const { data } = await fetchPosts({ keyword: keyword.value.trim(), size: 20 })
    results.value = data?.content ?? []
  } catch {
    results.value = []
  } finally {
    searching.value = false
  }
}

function selectPost(post) {
  selectedPost.value = post
}

async function confirmAdd({ stage, subCategory }) {
  try {
    await addCurriculumPost({ postId: selectedPost.value.id, stage, subCategory })
    toastStore.show('커리큘럼에 추가했습니다')
    emit('added')
    close()
  } catch {
    toastStore.show('추가에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}

function close() {
  emit('close')
}

function handleKeydown(event) {
  if (event.key === 'Escape') close()
}

onMounted(() => document.addEventListener('keydown', handleKeydown))
onUnmounted(() => document.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal-overlay" @click.self="close">
      <div class="modal-panel" role="dialog" aria-modal="true" aria-label="커리큘럼에 게시글 추가">
        <button class="modal-close" aria-label="닫기" @click="close">✕</button>
        <div class="modal-body">
          <h3 class="modal-title">커리큘럼에 게시글 추가</h3>

          <template v-if="!selectedPost">
            <div class="search-row">
              <input
                v-model="keyword"
                class="search-input"
                type="text"
                placeholder="게시글 내용, 작성자로 검색"
                @keydown.enter="search"
              />
              <span class="search-btn" @click="search">검색</span>
            </div>

            <div class="result-list">
              <div v-if="searching" class="empty-msg">검색 중...</div>
              <div v-else-if="keyword && results.length === 0" class="empty-msg">검색 결과가 없습니다.</div>
              <div v-for="post in results" :key="post.id" class="result-item" @click="selectPost(post)">
                <div class="result-author">{{ post.userName }}</div>
                <div class="result-content">{{ post.content }}</div>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="selected-post">
              <span class="selected-clear" @click="selectedPost = null">← 다시 검색</span>
              <div class="result-author">{{ selectedPost.userName }}</div>
              <div class="result-content">{{ selectedPost.content }}</div>
            </div>
            <CurriculumPicker confirm-label="추가" @confirm="confirmAdd" @cancel="close" />
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 26, 46, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.modal-panel {
  position: relative;
  width: 100%;
  max-width: 480px;
  max-height: 85vh;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(26, 26, 46, 0.25);
  overflow-y: auto;
}

.modal-body {
  padding: 24px;
}

.modal-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
}

.modal-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: none;
  background: rgba(26, 26, 46, 0.06);
  color: #1a1a2e;
  font-size: 14px;
  cursor: pointer;
  z-index: 1;
}

.modal-close:hover {
  background: rgba(26, 26, 46, 0.12);
}

.search-row {
  display: flex;
  gap: 8px;
}

.search-input {
  flex: 1;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(26, 26, 46, 0.15);
  font-size: 14px;
  font-family: inherit;
}

.search-btn {
  padding: 10px 16px;
  border-radius: 10px;
  background: #4a3f8f;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.result-list {
  margin-top: 12px;
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: #fafafa;
  cursor: pointer;
}

.result-item:hover {
  background: #f1eefc;
}

.result-author {
  font-size: 12.5px;
  font-weight: 700;
  color: #4a3f8f;
}

.result-content {
  margin-top: 2px;
  font-size: 13px;
  color: #1a1a2e;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-msg {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: #636e72;
}

.selected-post {
  position: relative;
  padding: 12px;
  border-radius: 10px;
  background: #fafafa;
  margin-bottom: 14px;
}

.selected-clear {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #4a3f8f;
  cursor: pointer;
}
</style>
