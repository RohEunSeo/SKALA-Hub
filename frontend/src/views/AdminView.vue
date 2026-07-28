<script setup>
// 관리자 전용 화면 - 전체 동기화 / 미분류 게시글 일괄 분류 / 게시글별 카테고리·태그·고정 수동 수정
import { ref, reactive, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { usePostsStore } from '../stores/posts'
import { fetchPosts } from '../api/posts'
import {
  triggerSync,
  fetchUncategorizedPosts,
  updatePostAsAdmin,
  classifyAllUncategorized,
} from '../api/admin'
import { stripSlackMarkdown } from '../utils/renderSlackText'
import { formatRelativeTime } from '../utils/relativeTime'
import { CATEGORIES } from '../constants/categories'

const PAGE_SIZE = 10
const MANAGE_PAGE_SIZE = 30

const authStore = useAuthStore()
const toastStore = useToastStore()
const postsStore = usePostsStore()

// 전체 동기화
const syncing = ref(false)
const syncResult = ref(null)
const syncError = ref('')

async function runSync() {
  syncing.value = true
  syncError.value = ''
  syncResult.value = null
  try {
    const { data } = await triggerSync()
    syncResult.value = data
  } catch (error) {
    syncError.value = error.response?.data?.error || '동기화 중 오류가 발생했습니다.'
  } finally {
    syncing.value = false
  }
}

// 미분류 게시글
const uncategorizedPosts = ref([])
const uncategorizedTotal = ref(0)
const uncategorizedPage = ref(0)
const uncategorizedLoading = ref(false)
const uncategorizedError = ref('')
const classifying = ref(false)
const classifyResultCount = ref(null)
const classifyError = ref('')

async function loadUncategorized() {
  uncategorizedLoading.value = true
  uncategorizedError.value = ''
  try {
    const { data } = await fetchUncategorizedPosts(uncategorizedPage.value, PAGE_SIZE)
    uncategorizedPosts.value = data?.content ?? []
    uncategorizedTotal.value = data?.totalElements ?? 0
  } catch {
    uncategorizedError.value = '미분류 게시글을 불러오지 못했습니다.'
  } finally {
    uncategorizedLoading.value = false
  }
}

async function runClassifyAll() {
  classifying.value = true
  classifyResultCount.value = null
  classifyError.value = ''
  try {
    const { data } = await classifyAllUncategorized()
    classifyResultCount.value = data.classified
    uncategorizedPage.value = 0
    allPostsPage.value = 0
    await loadUncategorized()
    await loadAllPosts(true)
    postsStore.refreshCategoryCounts()
  } catch {
    classifyError.value = '일괄 분류에 실패했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    classifying.value = false
  }
}

// 전체 게시글 관리 (카테고리/태그/핀 수동 수정)
const allPosts = ref([])
const allPostsTotal = ref(0)
const allPostsTotalPages = ref(0)
const allPostsPage = ref(0)
const allPostsLoading = ref(false)
const allPostsError = ref('')
const editState = reactive({})
const savingId = ref(null)
const savedId = ref(null)
const manageCategoryFilter = ref('') // '' = 전체 카테고리
const allPostsHasMore = computed(() => allPostsPage.value + 1 < allPostsTotalPages.value)

function selectManageCategory(value) {
  manageCategoryFilter.value = value
  allPostsPage.value = 0
  loadAllPosts(true)
}

function categoryTagOptions(categoryValue) {
  return CATEGORIES.find((cat) => cat.value === categoryValue)?.tags ?? []
}

function categoryLabel(value) {
  return CATEGORIES.find((cat) => cat.value === value)?.label ?? '미분류'
}

function initEditState(posts) {
  for (const post of posts) {
    editState[post.id] = {
      category: post.category || '',
      tags: [...(post.tags || [])],
      isPinned: !!post.isPinned,
      tagInput: '',
    }
  }
}

// 카테고리에 정해진 태그 목록 없이도 자유롭게 태그를 추가할 수 있게 함 (백엔드 tags 컬럼은 자유 문자열 배열)
function addCustomTag(postId) {
  const state = editState[postId]
  const value = state.tagInput.trim()
  if (!value) return
  if (!state.tags.includes(value)) {
    state.tags.push(value)
  }
  state.tagInput = ''
}

function removeTag(postId, tagValue) {
  const state = editState[postId]
  state.tags = state.tags.filter((tag) => tag !== tagValue)
}

// reset=true: 카테고리 필터를 바꿨을 때 목록을 처음부터 새로 조회, false: "더보기"로 다음 페이지를 이어붙임
async function loadAllPosts(reset = false) {
  allPostsLoading.value = true
  allPostsError.value = ''
  try {
    const { data } = await fetchPosts({
      category: manageCategoryFilter.value || undefined,
      page: allPostsPage.value,
      size: MANAGE_PAGE_SIZE,
    })
    const content = data?.content ?? []
    allPosts.value = reset ? content : [...allPosts.value, ...content]
    allPostsTotal.value = data?.totalElements ?? 0
    allPostsTotalPages.value = data?.totalPages ?? 0
    initEditState(content)
  } catch {
    allPostsError.value = '게시글 목록을 불러오지 못했습니다.'
    if (reset) allPosts.value = []
  } finally {
    allPostsLoading.value = false
  }
}

function loadMoreAllPosts() {
  allPostsPage.value += 1
  loadAllPosts()
}

function onCategoryChange(postId) {
  // 카테고리를 바꾸면 태그는 새 카테고리 기준으로 초기화 (예: 학습자료 -> 다른 카테고리로 바꾸면 태그 제거)
  editState[postId].tags = []
}

async function savePost(postId) {
  savingId.value = postId
  savedId.value = null
  try {
    const state = editState[postId]
    const { data } = await updatePostAsAdmin(postId, {
      category: state.category || null,
      tags: state.tags,
      isPinned: state.isPinned,
    })
    const index = allPosts.value.findIndex((post) => post.id === postId)
    const movedOutOfFilter = manageCategoryFilter.value !== '' && data.category !== manageCategoryFilter.value
    if (index !== -1) {
      if (movedOutOfFilter) {
        // 현재 카테고리 필터와 다른 카테고리로 옮겨졌으므로 필터된 목록에서는 제거
        allPosts.value.splice(index, 1)
        allPostsTotal.value -= 1
      } else {
        allPosts.value[index] = data
      }
    }
    savedId.value = postId
    toastStore.show(`${categoryLabel(data.category)} 카테고리로 이동했습니다`)
    postsStore.refreshCategoryCounts()
  } catch {
    toastStore.show('저장에 실패했습니다. 잠시 후 다시 시도해주세요.')
  } finally {
    savingId.value = null
  }
}

onMounted(() => {
  if (authStore.user?.role !== 'admin') return
  loadUncategorized()
  loadAllPosts(true)
})
</script>

<template>
  <AppLayout :max-width="1040">
    <AuthRequired v-if="!authStore.isAuthenticated" message="관리자 페이지는 로그인이 필요합니다" />
    <div v-else-if="authStore.user?.role !== 'admin'" class="no-permission">
      <div class="no-permission-icon">🚫</div>
      <div class="no-permission-message">관리자 권한이 없습니다.</div>
    </div>
    <template v-else>
      <h1 class="page-title">🛡️ 관리자 모드</h1>

      <section class="section">
        <div class="section-title">🔄 전체 동기화</div>
        <div class="card">
          <p class="card-desc">슬랙 채널의 전체 게시글/댓글을 다시 수집하고 미분류 게시글을 분류합니다.</p>
          <button class="primary-btn" :disabled="syncing" @click="runSync">
            {{ syncing ? '동기화 중...' : '지금 전체 동기화' }}
          </button>
          <div v-if="syncResult" class="result-box">
            처리 {{ syncResult.postsProcessed }}건 · 신규 {{ syncResult.newPosts }}건 · 댓글
            {{ syncResult.repliesProcessed }}건 · {{ (syncResult.durationMs / 1000).toFixed(1) }}초 소요
          </div>
          <div v-if="syncError" class="result-box error">{{ syncError }}</div>
        </div>
      </section>

      <section class="section">
        <div class="section-title">📂 미분류 게시글 ({{ uncategorizedTotal }}개)</div>
        <div class="card">
          <button class="primary-btn" :disabled="classifying || uncategorizedTotal === 0" @click="runClassifyAll">
            {{ classifying ? '분류 중...' : `미분류 ${uncategorizedTotal}개 전체 일괄 분류` }}
          </button>
          <div v-if="classifyResultCount !== null" class="result-box">
            {{ classifyResultCount }}개 게시글을 분류했습니다.
          </div>
          <div v-if="classifyError" class="result-box error">{{ classifyError }}</div>

          <div v-if="uncategorizedLoading" class="status-message">불러오는 중...</div>
          <div v-else-if="uncategorizedError" class="status-message error">{{ uncategorizedError }}</div>
          <div v-else-if="uncategorizedPosts.length === 0" class="status-message">미분류 게시글이 없습니다.</div>
          <div v-else class="uncategorized-list">
            <div v-for="post in uncategorizedPosts" :key="post.id" class="uncategorized-row">
              <div class="manage-header">
                <span class="row-author">{{ post.userName }}</span>
                <span class="row-time">{{ formatRelativeTime(post.createdAt) }}</span>
              </div>
              <div class="row-preview">{{ stripSlackMarkdown(post.content) }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section-title">📝 게시글 카테고리·태그·고정 관리 (총 {{ allPostsTotal }}개)</div>

        <div class="category-chips">
          <span
            class="chip"
            :class="{ active: manageCategoryFilter === '' }"
            @click="selectManageCategory('')"
            >전체</span
          >
          <span
            v-for="cat in CATEGORIES"
            :key="cat.value"
            class="chip"
            :class="{ active: manageCategoryFilter === cat.value }"
            @click="selectManageCategory(cat.value)"
            >{{ cat.icon }} {{ cat.shortLabel }}</span
          >
        </div>

        <div v-if="allPostsLoading" class="status-message">불러오는 중...</div>
        <div v-else-if="allPostsError" class="status-message error">{{ allPostsError }}</div>
        <div v-else-if="allPosts.length === 0" class="status-message">해당 카테고리에 게시글이 없습니다.</div>
        <div v-else class="post-manage-list">
          <div v-for="post in allPosts" :key="post.id" class="manage-row">
            <div class="manage-header">
              <span class="row-author">{{ post.userName }}</span>
              <span class="row-time">{{ formatRelativeTime(post.createdAt) }}</span>
            </div>
            <div class="manage-preview">{{ stripSlackMarkdown(post.content) }}</div>

            <div v-if="editState[post.id]" class="manage-controls">
              <div class="control-row">
                <select v-model="editState[post.id].category" class="category-select" @change="onCategoryChange(post.id)">
                  <option value="">미분류</option>
                  <option v-for="cat in CATEGORIES" :key="cat.value" :value="cat.value">{{ cat.label }}</option>
                </select>

                <label class="pin-checkbox">
                  <input type="checkbox" v-model="editState[post.id].isPinned" />
                  📌 고정
                </label>

                <button class="save-btn" :disabled="savingId === post.id" @click="savePost(post.id)">
                  {{ savingId === post.id ? '저장 중...' : '저장' }}
                </button>
                <span v-if="savedId === post.id" class="saved-check">✅ 저장됨</span>
              </div>

              <div class="control-row tag-row">
                <div v-if="categoryTagOptions(editState[post.id].category).length" class="tag-checkboxes">
                  <label v-for="tagOption in categoryTagOptions(editState[post.id].category)" :key="tagOption.value">
                    <input type="checkbox" :value="tagOption.value" v-model="editState[post.id].tags" />
                    {{ tagOption.label }}
                  </label>
                </div>

                <span v-for="tagValue in editState[post.id].tags" :key="tagValue" class="tag-chip">
                  {{ tagValue }}
                  <span class="tag-chip-remove" @click="removeTag(post.id, tagValue)">×</span>
                </span>

                <input
                  v-model="editState[post.id].tagInput"
                  class="tag-input"
                  type="text"
                  placeholder="태그 추가 후 Enter"
                  @keyup.enter="addCustomTag(post.id)"
                />
                <button class="tag-add-btn" type="button" @click="addCustomTag(post.id)">+ 추가</button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="allPostsHasMore && !allPostsLoading" class="load-more" @click="loadMoreAllPosts">더보기</div>
      </section>
    </template>
  </AppLayout>
</template>

<style scoped>
.no-permission {
  background: #ffffff;
  border-radius: 16px;
  padding: 56px 32px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.no-permission-icon {
  font-size: 32px;
  margin-bottom: 14px;
}

.no-permission-message {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
}

.page-title {
  font-size: 22px;
  font-weight: 800;
  color: #1a1a2e;
  margin-bottom: 24px;
}

.section {
  margin-bottom: 40px;
}

.section-title {
  font-size: 15px;
  font-weight: 800;
  color: #1a1a2e;
  margin-bottom: 12px;
}

.card {
  background: #ffffff;
  border-radius: 14px;
  padding: 20px 22px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
}

.card-desc {
  font-size: 13px;
  color: #636e72;
  margin-bottom: 14px;
}

.primary-btn {
  padding: 10px 20px;
  background: #4a3f8f;
  color: #fff;
  border: none;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn:disabled {
  background: #b7b0d9;
  cursor: not-allowed;
}

.primary-btn:not(:disabled):hover {
  background: #6c5ce7;
}

.result-box {
  margin-top: 12px;
  padding: 10px 14px;
  background: #f1eefc;
  color: #4a3f8f;
  border-radius: 8px;
  font-size: 12.5px;
}

.result-box.error {
  background: #fbe9ee;
  color: #e0607d;
}

.status-message {
  margin-top: 12px;
  text-align: center;
  color: #636e72;
  font-size: 13px;
}

.status-message.error {
  color: #e01e5a;
}

.uncategorized-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.uncategorized-row {
  padding: 10px 0;
  border-top: 1px solid rgba(26, 26, 46, 0.06);
  font-size: 12.5px;
}

.row-author {
  font-weight: 700;
  color: #1a1a2e;
  flex-shrink: 0;
}

.row-preview {
  margin-top: 4px;
  color: #636e72;
  white-space: pre-wrap;
  word-break: break-word;
}

.row-time {
  flex-shrink: 0;
  color: #636e72;
}

.category-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.chip {
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  color: #636e72;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.05);
  cursor: pointer;
}

.chip.active {
  background: #4a3f8f;
  color: #ffffff;
}

.post-manage-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.manage-row {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.05);
}

.manage-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.manage-preview {
  margin-top: 6px;
  font-size: 13.5px;
  color: #1a1a2e;
  white-space: pre-wrap;
  word-break: break-word;
}

.manage-controls {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(26, 26, 46, 0.06);
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.category-select {
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  font-size: 12.5px;
  font-family: inherit;
  background: #ffffff;
  color: #1a1a2e;
}

.tag-checkboxes {
  display: flex;
  gap: 10px;
  font-size: 12.5px;
  color: #636e72;
}

.tag-checkboxes label,
.pin-checkbox {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 12.5px;
  color: #636e72;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1eefc;
  color: #4a3f8f;
  font-size: 12px;
  font-weight: 600;
}

.tag-chip-remove {
  cursor: pointer;
  color: #8890a3;
  font-weight: 700;
}

.tag-chip-remove:hover {
  color: #e0607d;
}

.tag-input {
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  font-size: 12.5px;
  font-family: inherit;
  width: 140px;
}

.tag-add-btn {
  padding: 6px 12px;
  border-radius: 8px;
  border: none;
  background: #f1eefc;
  color: #4a3f8f;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.tag-add-btn:hover {
  background: #e3ddf7;
}

.save-btn {
  padding: 7px 16px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.saved-check {
  font-size: 12.5px;
  color: #2bb3a3;
  font-weight: 600;
}

.load-more {
  margin-top: 16px;
  text-align: center;
  padding: 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.1);
  color: #4a3f8f;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
}
</style>
