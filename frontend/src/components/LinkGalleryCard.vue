<script setup>
// 링크 모음 탭 - 게시글의 링크 미리보기(attachment) 1개를 카드로 표시
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePostsStore } from '../stores/posts'
import { updateLinkAsAdmin } from '../api/admin'
import { getLinkTheme, getLinkSource, hexToRgba } from '../utils/linkPreview'
import { CATEGORIES } from '../constants/categories'

const props = defineProps({
  post: { type: Object, required: true },
  attachment: { type: Object, required: true },
})

const router = useRouter()
const authStore = useAuthStore()
const postsStore = usePostsStore()

const theme = getLinkTheme(props.attachment.serviceName)
const linkUrl = props.attachment.titleLink || props.attachment.fromUrl
const categoryInfo = computed(() => CATEGORIES.find((cat) => cat.value === props.post.category))
// 관리자만 카드 위에서 바로 제목 수정/숨김 가능 - 별도 /admin 페이지 이동 없이
const isAdmin = computed(() => authStore.user?.role === 'admin')

const editing = ref(false)
const editTitle = ref('')
const editSource = ref('')

function goToDetail() {
  router.push({ name: 'post-detail', params: { id: props.post.id }, query: { from: 'links' } })
}

// 편집 중일 때는 카드 어디를 눌러도 외부 링크로 이동하지 않게 막음(입력창/버튼 자체는 각각 stop 처리됨)
function handleCardClick(event) {
  if (editing.value) {
    event.preventDefault()
  }
}

function startEdit() {
  editTitle.value = props.attachment.title || ''
  editSource.value = getLinkSource(props.attachment)
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function saveEdit() {
  await updateLinkAsAdmin({
    url: props.attachment.fromUrl,
    title: editTitle.value,
    source: editSource.value,
  })
  editing.value = false
  postsStore.fetchPosts(true)
}

// url 기준 전역 숨김 - 같은 링크가 다른 게시글에도 있으면 전부 함께 숨겨짐
async function hideLink() {
  if (!confirm('이 링크를 링크 모음에서 숨길까요? (같은 링크가 다른 게시글에도 있으면 전부 숨겨집니다)')) {
    return
  }
  await updateLinkAsAdmin({ url: props.attachment.fromUrl, hidden: true })
  postsStore.fetchPosts(true)
}
</script>

<template>
  <a class="link-card" :href="linkUrl" target="_blank" rel="noopener noreferrer" @click="handleCardClick">
    <div class="link-card-thumb" :style="{ background: theme.bg }">
      <img v-if="attachment.imageUrl" :src="attachment.imageUrl" alt="" />
      <span v-else class="link-card-emoji">{{ theme.emoji }}</span>
      <span
        v-if="categoryInfo"
        class="link-card-category-badge"
        :style="{ background: hexToRgba(categoryInfo.color, 0.72) }"
      >
        {{ categoryInfo.icon }} {{ categoryInfo.shortLabel }}
      </span>
      <div v-if="isAdmin" class="link-card-admin-actions">
        <span class="link-card-admin-btn" title="제목 수정" @click.stop.prevent="startEdit">✏️</span>
        <span class="link-card-admin-btn" title="숨김" @click.stop.prevent="hideLink">🗑️</span>
      </div>
    </div>
    <div class="link-card-body">
      <template v-if="editing">
        <input
          v-model="editSource"
          class="link-card-edit-input link-card-edit-source"
          placeholder="표시할 출처(도메인) 텍스트"
          @click.stop.prevent
          @keydown.stop
        />
        <input
          v-model="editTitle"
          class="link-card-edit-input"
          placeholder="표시할 제목"
          @click.stop.prevent
          @keydown.stop
        />
        <div class="link-card-edit-actions">
          <span class="link-card-edit-save" @click.stop.prevent="saveEdit">저장</span>
          <span class="link-card-edit-cancel" @click.stop.prevent="cancelEdit">취소</span>
        </div>
      </template>
      <template v-else>
        <div class="link-card-source">{{ getLinkSource(attachment) }}</div>
        <div class="link-card-title">{{ attachment.title || linkUrl }}</div>
        <div class="link-card-footer">
          <span class="link-card-stats">
            <span>👍 {{ post.reactionCount ?? 0 }}</span>
            <span>💬 {{ post.replyCount ?? 0 }}</span>
          </span>
          <span class="link-card-detail" @click.stop.prevent="goToDetail">게시글 보러가기</span>
        </div>
      </template>
    </div>
  </a>
</template>

<style scoped>
.link-card {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.link-card:hover {
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.1);
  transform: translateY(-2px);
}

.link-card-thumb {
  position: relative;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-bottom: 1px solid rgba(26, 26, 46, 0.08);
}

.link-card-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.link-card-emoji {
  font-size: 32px;
}

.link-card-category-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.2);
}

.link-card-admin-actions {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.link-card:hover .link-card-admin-actions {
  opacity: 1;
}

.link-card-admin-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(26, 26, 46, 0.2);
}

.link-card-admin-btn:hover {
  background: #ffffff;
}

.link-card-body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.link-card-source {
  font-size: 12px;
  color: #636e72;
}

.link-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
}

.link-card-footer {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.link-card-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #636e72;
  white-space: nowrap;
}

.link-card-detail {
  font-size: 12px;
  color: #1264a3;
  font-weight: 600;
  white-space: nowrap;
}

.link-card-detail:hover {
  text-decoration: underline;
}

.link-card-edit-input {
  width: 100%;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  border: 1px solid rgba(26, 26, 46, 0.15);
  border-radius: 8px;
  padding: 6px 8px;
  font-family: inherit;
}

.link-card-edit-input + .link-card-edit-input {
  margin-top: 6px;
}

.link-card-edit-source {
  font-size: 12px;
  font-weight: 400;
  color: #636e72;
}

.link-card-edit-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.link-card-edit-save,
.link-card-edit-cancel {
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 6px;
}

.link-card-edit-save {
  background: #4a3f8f;
  color: #ffffff;
}

.link-card-edit-cancel {
  background: #f1eefc;
  color: #4a3f8f;
}
</style>
