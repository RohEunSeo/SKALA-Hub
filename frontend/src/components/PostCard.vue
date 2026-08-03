<script setup>
// 게시글 한 건을 카드 형태로 보여주는 컴포넌트 (슬랙 원본 렌더링 + 저장/댓글/딥링크)
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useBookmarksStore } from '../stores/bookmarks'
import { useToastStore } from '../stores/toast'
import { fetchReplies } from '../api/posts'
import { renderSlackText, highlightInHtml } from '../utils/renderSlackText'
import { formatRelativeTime, formatDateTime } from '../utils/relativeTime'
import { getFileIcon } from '../utils/fileIcon'
import { CATEGORIES } from '../constants/categories'

const props = defineProps({
  post: { type: Object, required: true },
  // true(기본): 피드 목록 등에서 댓글 영역 클릭 시 게시글 상세 페이지로 이동
  // false: 상세 페이지 자체에서 사용 - 진입하자마자 댓글이 펼쳐진 상태로 보임
  linkToDetail: { type: Boolean, default: true },
  // 검색 중인 키워드가 있으면 본문에서 해당 부분을 하이라이트 표시
  highlightKeyword: { type: String, default: '' },
})

const router = useRouter()
const authStore = useAuthStore()
const bookmarksStore = useBookmarksStore()
const toastStore = useToastStore()

const showComments = ref(!props.linkToDetail)
const repliesLoaded = ref(false)
const repliesLoading = ref(false)
const repliesError = ref('')
const replies = ref([])

const apiBase = import.meta.env.VITE_API_BASE_URL

const categoryInfo = computed(() => CATEGORIES.find((cat) => cat.value === props.post.category))
const renderedContent = computed(() =>
  highlightInHtml(renderSlackText(props.post.content), props.highlightKeyword),
)
const createdAtLabel = computed(() => formatRelativeTime(props.post.createdAt))
const createdAtAbsolute = computed(() => formatDateTime(props.post.createdAt))
const isBookmarked = computed(() => bookmarksStore.bookmarkedPostIds.includes(props.post.id))

const imageFiles = computed(() => (props.post.files ?? []).filter((file) => file.isImage))
const otherFiles = computed(() => (props.post.files ?? []).filter((file) => !file.isImage))

// /api/files/proxy는 인증이 필요한데 <img src>는 브라우저가 직접 요청해서 Authorization 헤더를
// 못 붙이므로, 쿼리 파라미터로 토큰을 실어 보낸다 (JwtAuthFilter가 헤더 없으면 이 파라미터를 봐줌)
function proxySrc(path) {
  return authStore.token ? `${apiBase}${path}&token=${authStore.token}` : `${apiBase}${path}`
}

const lightboxIndex = ref(null)
const lightboxSrc = computed(() =>
  lightboxIndex.value === null ? null : proxySrc(imageFiles.value[lightboxIndex.value].proxyUrl),
)

function openImage(index) {
  lightboxIndex.value = index
}

function closeLightbox() {
  lightboxIndex.value = null
}

function showPrevImage() {
  lightboxIndex.value = (lightboxIndex.value - 1 + imageFiles.value.length) % imageFiles.value.length
}

function showNextImage() {
  lightboxIndex.value = (lightboxIndex.value + 1) % imageFiles.value.length
}

function handleLightboxKeydown(e) {
  if (lightboxIndex.value === null) return
  if (e.key === 'Escape') closeLightbox()
  else if (e.key === 'ArrowLeft') showPrevImage()
  else if (e.key === 'ArrowRight') showNextImage()
}

onMounted(() => {
  window.addEventListener('keydown', handleLightboxKeydown)
})
onUnmounted(() => {
  window.removeEventListener('keydown', handleLightboxKeydown)
})

async function loadReplies() {
  if (repliesLoaded.value || repliesLoading.value) return
  repliesLoading.value = true
  repliesError.value = ''
  try {
    const { data } = await fetchReplies(props.post.id)
    replies.value = data ?? []
    repliesLoaded.value = true
  } catch {
    repliesError.value = '댓글을 불러오지 못했습니다.'
  } finally {
    repliesLoading.value = false
  }
}

function handleCommentsClick() {
  if (props.linkToDetail) {
    router.push({ name: 'post-detail', params: { id: props.post.id } })
    return
  }
  showComments.value = !showComments.value
  if (showComments.value) {
    loadReplies()
  }
}

onMounted(() => {
  if (!props.linkToDetail) {
    loadReplies()
  }
})

async function toggleBookmark() {
  if (!authStore.isAuthenticated) return
  const wasBookmarked = isBookmarked.value
  try {
    await bookmarksStore.toggle(props.post.id)
    if (!wasBookmarked) {
      toastStore.show('저장되었습니다', {
        actionLabel: '저장한 글 보기',
        onAction: () => router.push('/mypage'),
      })
    }
  } catch {
    toastStore.show('저장에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}

function openInSlack() {
  if (props.post.slackPermalink) {
    window.open(props.post.slackPermalink, '_blank', 'noopener,noreferrer')
  }
}
</script>

<template>
  <article class="post-card" :class="{ pinned: post.isPinned }">
    <div v-if="post.isPinned" class="pinned-badge">📌 고정</div>

    <div class="post-header">
      <div class="author">
        <img v-if="post.userAvatarUrl" class="avatar avatar-img" :src="post.userAvatarUrl" :alt="post.userName" />
        <div v-else class="avatar">{{ post.userName?.charAt(0) }}</div>
        <span class="author-name">{{ post.userName }}</span>
        <span v-if="post.isInstructor" class="instructor-badge">· 전임교수</span>
      </div>
      <div class="header-right">
        <span class="post-time">
          {{ createdAtAbsolute }} · {{ createdAtLabel }}
          <span v-if="post.isEdited" class="edited-badge">(편집됨)</span>
        </span>
        <span class="header-stats">
          <span>👍 {{ post.reactionCount ?? 0 }}</span>
          <span class="comment-link" @click="handleCommentsClick">💬 {{ post.replyCount ?? 0 }}</span>
        </span>
      </div>
    </div>

    <div class="badges">
      <span v-if="categoryInfo" class="badge category-badge">{{ categoryInfo.icon }} {{ categoryInfo.shortLabel }}</span>
      <span v-for="postTag in post.tags" :key="postTag" class="badge tag-badge">🏷️ {{ postTag }}</span>
    </div>

    <!-- eslint-disable-next-line vue/no-v-html -->
    <div class="post-content" v-html="renderedContent"></div>

    <div v-if="imageFiles.length" class="image-files" :class="{ 'single-image': imageFiles.length === 1 }">
      <img
        v-for="(file, index) in imageFiles"
        :key="file.proxyUrl"
        :src="proxySrc(file.proxyUrl)"
        :alt="file.name"
        title="클릭하면 크게 볼 수 있습니다"
        @click="openImage(index)"
      />
    </div>

    <div v-if="otherFiles.length" class="attached-files">
      <div v-for="file in otherFiles" :key="file.name" class="file-row">
        {{ getFileIcon(file.filetype) }} {{ file.name }}
      </div>
      <div class="file-notice">* 원본 파일은 슬랙에서 다운받아 주세요.</div>
    </div>

    <Teleport to="body">
      <div v-if="lightboxSrc" class="lightbox-overlay" @click="closeLightbox">
        <button
          v-if="imageFiles.length > 1"
          class="lightbox-nav lightbox-prev"
          title="이전 이미지"
          @click.stop="showPrevImage"
        >
          ‹
        </button>
        <img :src="lightboxSrc" class="lightbox-image" alt="" @click.stop />
        <button
          v-if="imageFiles.length > 1"
          class="lightbox-nav lightbox-next"
          title="다음 이미지"
          @click.stop="showNextImage"
        >
          ›
        </button>
        <div v-if="imageFiles.length > 1" class="lightbox-counter">
          {{ lightboxIndex + 1 }} / {{ imageFiles.length }}
        </div>
        <button class="lightbox-close" title="닫기" @click.stop="closeLightbox">✕</button>
      </div>
    </Teleport>

    <div v-if="post.attachments?.length" class="link-previews">
      <a
        v-for="(attachment, index) in post.attachments"
        :key="index"
        class="link-preview"
        :href="attachment.titleLink || attachment.fromUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <img v-if="attachment.imageUrl" :src="attachment.imageUrl" class="link-thumb" alt="" />
        <div class="link-body">
          <div class="link-title">🔗 {{ attachment.title || attachment.fromUrl }}</div>
          <div v-if="attachment.text" class="link-text">{{ attachment.text }}</div>
        </div>
      </a>
    </div>

    <div class="stats">
      <span>👍 {{ post.reactionCount ?? 0 }}</span>
      <span class="comment-link" @click="handleCommentsClick"> 💬 댓글 {{ post.replyCount ?? 0 }}개 </span>
    </div>

    <div v-if="showComments" class="comments">
      <div v-if="repliesLoading" class="comment-loading">불러오는 중...</div>
      <div v-else-if="repliesError" class="comment-empty">{{ repliesError }}</div>
      <div v-for="reply in replies" :key="reply.id" class="comment-row">
        <img v-if="reply.userAvatarUrl" class="comment-avatar avatar-img" :src="reply.userAvatarUrl" :alt="reply.userName" />
        <div v-else class="comment-avatar">{{ reply.userName?.charAt(0) }}</div>
        <div class="comment-body">
          <div class="comment-header">
            <span class="comment-author">{{ reply.userName }}</span>
            <span class="comment-time">{{ formatRelativeTime(reply.createdAt) }}</span>
          </div>
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="comment-content" v-html="renderSlackText(reply.content)"></div>
        </div>
      </div>
      <div v-if="!repliesLoading && repliesLoaded && replies.length === 0" class="comment-empty">
        아직 댓글이 없습니다.
      </div>
    </div>

    <div class="actions">
      <span
        class="action save-action"
        :class="{ disabled: !authStore.isAuthenticated, active: isBookmarked }"
        :title="!authStore.isAuthenticated ? '로그인 후 이용할 수 있어요' : ''"
        @click="toggleBookmark"
      >
        {{ isBookmarked ? '✅ 저장됨' : '🔖 저장하기' }}
      </span>
      <span class="action" @click="openInSlack">💬 슬랙에서 보기</span>
    </div>
  </article>
</template>

<style scoped>
.post-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px 28px;
  box-shadow: 0 2px 12px rgba(26, 26, 46, 0.06);
  position: relative;
  max-width: 100%;
  overflow-wrap: break-word;
}

@media (max-width: 480px) {
  .post-card {
    padding: 18px 16px;
  }
}

.post-card.pinned {
  border: 1px solid rgba(108, 92, 231, 0.25);
}

.pinned-badge {
  position: absolute;
  top: -10px;
  left: 24px;
  background: #4a3f8f;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 6px;
}

.post-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.author {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.header-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: #636e72;
  flex-shrink: 0;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #efecfb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #4a3f8f;
  font-size: 13px;
}

.avatar-img {
  object-fit: cover;
  background: #f4f4f4;
}

.author-name {
  font-weight: 700;
  font-size: 14px;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.instructor-badge {
  font-size: 12px;
  color: #636e72;
}

.post-time {
  font-size: 12px;
  color: #636e72;
  white-space: nowrap;
  flex-shrink: 0;
}

.edited-badge {
  color: #a0a4b8;
}

.badges {
  margin-top: 12px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
}

.category-badge {
  background: #f1eefc;
  color: #4a3f8f;
}

.tag-badge {
  background: #f4f4f4;
  color: #636e72;
}

.post-content {
  margin-top: 14px;
  font-size: 15px;
  color: #1a1a2e;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  tab-size: 2;
}

.post-content :deep(.slack-inline-code) {
  background: #f7e0d9;
  color: #e01e5a;
  font-family: ui-monospace, monospace;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.9em;
}

.post-content :deep(.slack-codeblock) {
  background: #f8f8f8;
  border: 1px solid rgba(26, 26, 46, 0.08);
  border-radius: 8px;
  padding: 12px 14px;
  overflow-x: auto;
  font-family: ui-monospace, monospace;
  font-size: 13px;
  margin: 8px 0;
  white-space: pre-wrap;
}

.post-content :deep(.slack-quote) {
  border-left: 3px solid rgba(26, 26, 46, 0.15);
  padding-left: 12px;
  margin: 8px 0;
}

.post-content :deep(.slack-link) {
  color: #1264a3;
  text-decoration: none;
}

.post-content :deep(.slack-link:hover) {
  text-decoration: underline;
}

.post-content :deep(.slack-link-line) {
  display: inline-flex;
  background: rgba(18, 100, 163, 0.06);
  border-radius: 6px;
  padding: 4px 10px;
  margin: 4px 0;
}

.post-content :deep(.slack-mention) {
  color: #1264a3;
  background: #e8f5fa;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 600;
}

.post-content :deep(.slack-highlight) {
  background: #fff3a3;
  color: #1a1a2e;
  border-radius: 3px;
  padding: 0 2px;
}

/* 사진 1장이면 크게, 여러 장이면 원본 비율 유지한 채 작은 썸네일로 줄맞춰 나열 (잘라내지 않음) */
.image-files {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.image-files img {
  max-width: 220px;
  max-height: 180px;
  border-radius: 10px;
  display: block;
  object-fit: contain;
  cursor: zoom-in;
}

.image-files.single-image img {
  max-width: 100%;
  max-height: 420px;
}

@media (max-width: 480px) {
  .image-files img {
    max-width: 150px;
    max-height: 130px;
  }

  .image-files.single-image img {
    max-width: 100%;
    max-height: 320px;
  }
}

.lightbox-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 1000;
  cursor: zoom-out;
}

.lightbox-image {
  max-width: 100%;
  max-height: 100%;
  border-radius: 8px;
  cursor: default;
}

.lightbox-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border: none;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lightbox-nav:hover {
  background: rgba(255, 255, 255, 0.25);
}

.lightbox-prev {
  left: 16px;
}

.lightbox-next {
  right: 16px;
}

.lightbox-counter {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  color: #fff;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.4);
  padding: 4px 12px;
  border-radius: 12px;
}

.lightbox-close {
  position: absolute;
  top: 16px;
  right: 16px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lightbox-close:hover {
  background: rgba(255, 255, 255, 0.25);
}

.attached-files {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fafafa;
  border-radius: 10px;
  font-size: 13px;
  color: #1a1a2e;
}

.file-notice {
  font-size: 12px;
  color: #636e72;
}

.link-previews {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.link-preview {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  background: #fafafa;
  border-radius: 10px;
  text-decoration: none;
}

.link-thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
  flex-shrink: 0;
}

.link-title {
  font-size: 13px;
  color: #4a3f8f;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.link-text {
  margin-top: 2px;
  font-size: 12px;
  color: #636e72;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stats {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 14px;
  color: #636e72;
}

.comment-link {
  cursor: pointer;
  color: #1264a3;
  font-weight: 600;
}

.comment-link:hover {
  text-decoration: underline;
}

.comments {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(26, 26, 46, 0.06);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-loading,
.comment-empty {
  font-size: 13px;
  color: #636e72;
}

.comment-row {
  display: flex;
  gap: 10px;
}

.comment-avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #efecfb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #4a3f8f;
  font-size: 12px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.comment-author {
  color: #1a1a2e;
  font-weight: 700;
  font-size: 13px;
}

.comment-time {
  color: #636e72;
  font-size: 11.5px;
}

.comment-content {
  margin-top: 3px;
  font-size: 13.5px;
  color: #1a1a2e;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  tab-size: 2;
}

.comment-content :deep(.slack-inline-code) {
  background: #f7e0d9;
  color: #e01e5a;
  font-family: ui-monospace, monospace;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.9em;
}

.comment-content :deep(.slack-link) {
  color: #1264a3;
  text-decoration: none;
}

.comment-content :deep(.slack-link-line) {
  display: inline-flex;
  background: rgba(18, 100, 163, 0.06);
  border-radius: 6px;
  padding: 3px 8px;
  margin: 3px 0;
}

.comment-content :deep(.slack-mention) {
  color: #1264a3;
  background: #e8f5fa;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 600;
}

.actions {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(26, 26, 46, 0.06);
  display: flex;
  gap: 20px;
}

.action {
  font-size: 13px;
  font-weight: 600;
  color: #636e72;
  cursor: pointer;
}

.save-action {
  color: #4a3f8f;
}

.save-action.active {
  color: #6c5ce7;
}

.save-action.disabled {
  color: #b2b2b2;
  cursor: not-allowed;
}
</style>
