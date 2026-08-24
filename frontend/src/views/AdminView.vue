<script setup>
// 관리자 전용 화면 - 전체 동기화 / 미분류 게시글 일괄 분류 / 게시글별 카테고리·태그·고정 수동 수정
import { ref, reactive, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import { usePostsStore } from '../stores/posts'
import { useHomeStore } from '../stores/home'
import { useMyPageStore } from '../stores/mypage'
import { fetchPosts } from '../api/posts'
import {
  triggerSync,
  triggerFullSync,
  fetchUncategorizedPosts,
  updatePostAsAdmin,
  classifyAllUncategorized,
  fetchSyncFailures,
  fetchBotReplies,
  deleteBotReply,
  updateBotReply,
  sendPendingNotification,
  backfillLinkPreviews,
  fetchAdminAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
} from '../api/admin'
import { stripSlackMarkdown, renderSlackText } from '../utils/renderSlackText'
import { formatRelativeTime } from '../utils/relativeTime'
import { CATEGORIES } from '../constants/categories'
import { ANNOUNCEMENT_LINK_PRESETS } from '../constants/announcementLinks'

const PAGE_SIZE = 10
const MANAGE_PAGE_SIZE = 4
const MANAGE_PREVIEW_LENGTH = 150

const authStore = useAuthStore()
const toastStore = useToastStore()
const postsStore = usePostsStore()
const homeStore = useHomeStore()
const myPageStore = useMyPageStore()

// 동기화로 게시글/댓글/반응 데이터가 바뀌었으니, 피드·홈·마이페이지 캐시를 무효화해서 다음 방문 때 새로 불러오게 함
function invalidateFeedCaches() {
  postsStore.invalidateCache()
  homeStore.invalidateCache()
  myPageStore.invalidateCache()
}

// 동기화 (최근 N일 - 평소에 쓰는 가벼운 동기화)
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
    invalidateFeedCaches()
    loadSyncFailures()
    loadBotReplies()
  } catch (error) {
    syncError.value = error.response?.data?.error || '동기화 중 오류가 발생했습니다.'
  } finally {
    syncing.value = false
  }
}

// 전체 재수집 (무거움 - 채널 히스토리 전체를 다시 훑음, 매일 새벽 자동 실행되므로 급할 때만 수동 실행)
const syncingFull = ref(false)
const syncFullResult = ref(null)
const syncFullError = ref('')

async function runFullSync() {
  syncingFull.value = true
  syncFullError.value = ''
  syncFullResult.value = null
  try {
    const { data } = await triggerFullSync()
    syncFullResult.value = data
    invalidateFeedCaches()
    loadSyncFailures()
    loadBotReplies()
  } catch (error) {
    syncFullError.value = error.response?.data?.error || '전체 재수집 중 오류가 발생했습니다.'
  } finally {
    syncingFull.value = false
  }
}

// 링크 미리보기 재수집 (슬랙 재수집 없음 - 이미 있는 게시글 본문에서 아직 캐시 안 된 링크만 다시 fetch)
const syncingLinks = ref(false)
const syncLinksResult = ref(null)
const syncLinksError = ref('')

async function runLinkPreviewBackfill() {
  syncingLinks.value = true
  syncLinksError.value = ''
  syncLinksResult.value = null
  try {
    const { data } = await backfillLinkPreviews()
    syncLinksResult.value = data
    invalidateFeedCaches()
  } catch (error) {
    syncLinksError.value = error.response?.data?.error || '링크 미리보기 재수집 중 오류가 발생했습니다.'
  } finally {
    syncingLinks.value = false
  }
}

// 동기화 실패 목록 - 슬랙엔 알리지 않고 여기서만 확인. 원인 고친 뒤 위 "지금 동기화"를 다시 누르면
// 성공한 항목은 자동으로 이 목록에서 빠짐
const syncFailures = ref([])
const syncFailuresLoading = ref(false)
const syncFailuresError = ref('')

async function loadSyncFailures() {
  syncFailuresLoading.value = true
  syncFailuresError.value = ''
  try {
    const { data } = await fetchSyncFailures()
    syncFailures.value = data ?? []
  } catch {
    syncFailuresError.value = '동기화 실패 목록을 불러오지 못했습니다.'
  } finally {
    syncFailuresLoading.value = false
  }
}

// 슬랙 봇이 남긴 동기화 안내 댓글 - 슬랙 채널에서는 수정/삭제가 안 돼서 여기서 관리
const BOT_REPLY_PAGE_SIZE = 4
const botReplies = ref([])
const botRepliesLoading = ref(false)
const botRepliesError = ref('')
const editingReplyId = ref(null)
const editingContent = ref('')
const botReplyActionError = ref('')
const botReplyTypeFilter = ref('all') // 'all' | 'success' | 'failure' | 'pending'
const botReplyDateFilter = ref('all') // 'all' | 'today'
const botReplyPage = ref(0)
const sendingPendingId = ref(null)

function isToday(dateStr) {
  const d = new Date(dateStr)
  const now = new Date()
  return (
    d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  )
}

const filteredBotReplies = computed(() =>
  botReplies.value.filter((reply) => {
    const typeMatch = botReplyTypeFilter.value === 'all' || reply.status === botReplyTypeFilter.value
    const dateMatch = botReplyDateFilter.value === 'all' || isToday(reply.createdAt)
    return typeMatch && dateMatch
  }),
)

const botReplyTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredBotReplies.value.length / BOT_REPLY_PAGE_SIZE)),
)

const pagedBotReplies = computed(() => {
  const start = botReplyPage.value * BOT_REPLY_PAGE_SIZE
  return filteredBotReplies.value.slice(start, start + BOT_REPLY_PAGE_SIZE)
})

function selectBotReplyTypeFilter(value) {
  botReplyTypeFilter.value = value
  botReplyPage.value = 0
}

function selectBotReplyDateFilter(value) {
  botReplyDateFilter.value = value
  botReplyPage.value = 0
}

function goToPrevBotReplyPage() {
  if (botReplyPage.value > 0) botReplyPage.value -= 1
}

function goToNextBotReplyPage() {
  if (botReplyPage.value + 1 < botReplyTotalPages.value) botReplyPage.value += 1
}

async function loadBotReplies() {
  botRepliesLoading.value = true
  botRepliesError.value = ''
  try {
    const { data } = await fetchBotReplies()
    botReplies.value = data ?? []
  } catch {
    botRepliesError.value = '봇 댓글 목록을 불러오지 못했습니다.'
  } finally {
    botRepliesLoading.value = false
  }
}

function startEditReply(reply) {
  editingReplyId.value = reply.id
  editingContent.value = reply.content
  botReplyActionError.value = ''
}

function cancelEditReply() {
  editingReplyId.value = null
  editingContent.value = ''
}

async function saveEditReply(reply) {
  botReplyActionError.value = ''
  try {
    await updateBotReply(reply.ts, editingContent.value)
    reply.content = editingContent.value
    cancelEditReply()
    toastStore.show('댓글을 수정했습니다')
  } catch (error) {
    botReplyActionError.value = error.response?.data?.error || '댓글 수정에 실패했습니다. 잠시 후 다시 시도해주세요.'
  }
}

async function removeBotReply(reply) {
  if (!window.confirm('이 댓글을 슬랙에서 삭제할까요?')) return
  botReplyActionError.value = ''
  try {
    await deleteBotReply(reply.ts)
    botReplies.value = botReplies.value.filter((item) => item.id !== reply.id)
    botReplyPage.value = Math.min(botReplyPage.value, botReplyTotalPages.value - 1)
    toastStore.show('댓글을 삭제했습니다')
  } catch (error) {
    botReplyActionError.value = error.response?.data?.error || '댓글 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.'
  }
}

// 로컬 환경에서 동기화되어 보류된 알림을 배포 환경에서 지금 전송 - 여전히 로컬이면 서버가 막아줌
async function sendPending(reply) {
  sendingPendingId.value = reply.postId
  botReplyActionError.value = ''
  try {
    await sendPendingNotification(reply.postId)
    await loadBotReplies()
    toastStore.show('슬랙에 동기화 완료 댓글을 보냈습니다')
  } catch (error) {
    botReplyActionError.value = error.response?.data?.error || '전송에 실패했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    sendingPendingId.value = null
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
const classifyFailedCount = ref(0)
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
    classifyFailedCount.value = data.failed ?? 0
    uncategorizedPage.value = 0
    allPostsPage.value = 0
    await loadUncategorized()
    await loadAllPosts()
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
const manageTagFilter = ref(null) // 선택된 카테고리의 하위 태그 필터, null = 전체
const manageDateFilter = ref('all') // 'all' | 'today' - 오늘 올라온 글만 모아서 태그 관리할 때 사용
const expandedPostIds = ref(new Set())
const manageCategoryTags = computed(
  () => CATEGORIES.find((cat) => cat.value === manageCategoryFilter.value)?.tags ?? [],
)
const savingAll = ref(false)

function selectManageCategory(value) {
  manageCategoryFilter.value = value
  manageTagFilter.value = null
  allPostsPage.value = 0
  loadAllPosts()
}

function selectManageTag(value) {
  manageTagFilter.value = manageTagFilter.value === value ? null : value
  allPostsPage.value = 0
  loadAllPosts()
}

function selectManageDateFilter(value) {
  manageDateFilter.value = value
  allPostsPage.value = 0
  loadAllPosts()
}

function togglePostExpand(postId) {
  const next = new Set(expandedPostIds.value)
  if (next.has(postId)) {
    next.delete(postId)
  } else {
    next.add(postId)
  }
  expandedPostIds.value = next
}

function managePreview(content) {
  const stripped = stripSlackMarkdown(content, { collapseNewlines: false })
  return stripped.length > MANAGE_PREVIEW_LENGTH
    ? stripped.slice(0, MANAGE_PREVIEW_LENGTH) + '...'
    : stripped
}

function categoryTagOptions(categoryValue) {
  const cat = CATEGORIES.find((c) => c.value === categoryValue)
  return [...(cat?.tags ?? []), ...(cat?.adminOnlyTags ?? [])]
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

// 매번 현재 allPostsPage 기준으로 목록을 통째로 교체 (이전/다음 페이지네이션 - 누적 안 함)
async function loadAllPosts() {
  allPostsLoading.value = true
  allPostsError.value = ''
  try {
    const { data } = await fetchPosts({
      category: manageCategoryFilter.value || undefined,
      tag: manageTagFilter.value || undefined,
      date: manageDateFilter.value === 'today' ? 'today' : undefined,
      page: allPostsPage.value,
      size: MANAGE_PAGE_SIZE,
    })
    const content = data?.content ?? []
    allPosts.value = content
    allPostsTotal.value = data?.totalElements ?? 0
    allPostsTotalPages.value = data?.totalPages ?? 0
    initEditState(content)
  } catch {
    allPostsError.value = '게시글 목록을 불러오지 못했습니다.'
    allPosts.value = []
  } finally {
    allPostsLoading.value = false
  }
}

function goToPrevManagePage() {
  if (allPostsPage.value > 0) {
    allPostsPage.value -= 1
    loadAllPosts()
  }
}

function goToNextManagePage() {
  if (allPostsPage.value + 1 < allPostsTotalPages.value) {
    allPostsPage.value += 1
    loadAllPosts()
  }
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

// 현재 목록에 보이는 게시글을 순서대로 하나씩 저장 - 체크박스만 눌러두고 개별 저장 버튼을 매번 누르지 않아도 되게 함
async function saveAllPosts() {
  savingAll.value = true
  let successCount = 0
  let failCount = 0
  try {
    for (const post of allPosts.value) {
      const state = editState[post.id]
      if (!state) continue
      try {
        await updatePostAsAdmin(post.id, {
          category: state.category || null,
          tags: state.tags,
          isPinned: state.isPinned,
        })
        successCount += 1
      } catch {
        failCount += 1
      }
    }
    await loadAllPosts()
    postsStore.refreshCategoryCounts()
    toastStore.show(
      failCount === 0
        ? `${successCount}개 게시글이 저장되었습니다`
        : `${successCount}개 저장되었습니다 (${failCount}개 실패, 잠시 후 다시 시도해주세요)`,
    )
  } finally {
    savingAll.value = false
  }
}

// 공지 관리 - 작성한 공지는 저장 즉시 모든 유저의 "전체 공지" 탭에 노출됨
const announcements = ref([])
const announcementsLoading = ref(false)
const announcementsError = ref('')
const newAnnouncement = reactive({ badgeType: '공지', title: '', content: '', linkPath: '' })
const sendingAnnouncement = ref(false)
const editingAnnouncementId = ref(null)

// 예시 불러오기 - 클릭하면 폼에 채워지고, 등록 전에 자유롭게 수정 가능
const ANNOUNCEMENT_PRESETS = [
  {
    label: '공지 예시',
    badgeType: '공지',
    title: '카테고리 분류 기능이 업데이트되었습니다',
    content: '학습자료 하위 태그(영상/아티클/깃허브) 분류 정확도가 개선되었습니다.',
  },
  {
    label: '버그 예시',
    badgeType: '버그',
    title: '이미지 로딩 오류가 수정되었습니다',
    content: '일부 게시글에서 이미지가 안 뜨던 문제를 고쳤습니다.',
  },
  {
    label: '업데이트 예시',
    badgeType: '업데이트',
    title: '링크 모음 탭이 새로 추가되었습니다',
    content: '피드 상단에서 🔗 링크 모음 탭으로 바로 이동할 수 있습니다.',
  },
]

function applyAnnouncementPreset(preset) {
  newAnnouncement.badgeType = preset.badgeType
  newAnnouncement.title = preset.title
  newAnnouncement.content = preset.content
}

function applyAnnouncementLinkPreset(preset) {
  newAnnouncement.linkPath = preset.path
}

function resetAnnouncementForm() {
  editingAnnouncementId.value = null
  newAnnouncement.badgeType = '공지'
  newAnnouncement.title = ''
  newAnnouncement.content = ''
  newAnnouncement.linkPath = ''
}

function startEditAnnouncement(a) {
  editingAnnouncementId.value = a.id
  newAnnouncement.badgeType = a.badgeType
  newAnnouncement.title = a.title
  newAnnouncement.content = a.content ?? ''
  newAnnouncement.linkPath = a.linkPath ?? ''
}

async function loadAnnouncements() {
  announcementsLoading.value = true
  announcementsError.value = ''
  try {
    const { data } = await fetchAdminAnnouncements()
    announcements.value = data
  } catch {
    announcementsError.value = '공지 목록을 불러오지 못했습니다.'
  } finally {
    announcementsLoading.value = false
  }
}

async function submitAnnouncement() {
  if (!newAnnouncement.title.trim()) return
  sendingAnnouncement.value = true
  try {
    if (editingAnnouncementId.value) {
      await updateAnnouncement(editingAnnouncementId.value, { ...newAnnouncement })
      toastStore.show('공지가 수정되었습니다')
    } else {
      await createAnnouncement({ ...newAnnouncement })
      toastStore.show('공지가 등록되었습니다')
    }
    resetAnnouncementForm()
    await loadAnnouncements()
  } catch {
    toastStore.show(editingAnnouncementId.value ? '공지 수정에 실패했습니다' : '공지 등록에 실패했습니다')
  } finally {
    sendingAnnouncement.value = false
  }
}

async function removeAnnouncement(id) {
  try {
    await deleteAnnouncement(id)
    if (editingAnnouncementId.value === id) resetAnnouncementForm()
    await loadAnnouncements()
  } catch {
    toastStore.show('공지 삭제에 실패했습니다')
  }
}

onMounted(() => {
  if (!authStore.effectiveIsAdmin) return
  loadUncategorized()
  loadAllPosts()
  loadSyncFailures()
  loadBotReplies()
  loadAnnouncements()
})
</script>

<template>
  <AppLayout :max-width="1040">
    <AuthRequired v-if="!authStore.isAuthenticated" message="관리자 페이지는 로그인이 필요합니다" />
    <div v-else-if="!authStore.effectiveIsAdmin" class="no-permission">
      <div class="no-permission-icon">🚫</div>
      <div class="no-permission-message">관리자 권한이 없습니다.</div>
    </div>
    <template v-else>
      <h1 class="page-title">🛡️ 관리자 모드</h1>

      <section class="section">
        <div class="section-title">🔄 동기화</div>
        <div class="card sync-layout">
          <div class="sync-controls">
            <p class="card-desc">최근 7일 이내 게시글/댓글만 다시 수집하고 미분류 게시글을 분류합니다. API 호출량이 적어 자주 눌러도 괜찮습니다.</p>
            <button class="primary-btn" :disabled="syncing" @click="runSync">
              {{ syncing ? '동기화 중...' : '지금 동기화' }}
            </button>
            <div v-if="syncResult" class="result-box">
              처리 {{ syncResult.postsProcessed }}건 · 신규 {{ syncResult.newPosts }}건 · 댓글
              {{ syncResult.repliesProcessed }}건 · {{ (syncResult.durationMs / 1000).toFixed(1) }}초 소요
            </div>
            <div v-if="syncError" class="result-box error">{{ syncError }}</div>

            <div class="full-sync-row">
              <p class="card-desc small">
                채널 히스토리 전체를 다시 훑어야 하는 경우(과거 데이터 복구 등)만 아래를 사용하세요.
                평소엔 매일 새벽 4시에 자동으로 실행됩니다.
              </p>
              <button class="secondary-btn" :disabled="syncingFull" @click="runFullSync">
                {{ syncingFull ? '전체 재수집 중...' : '전체 재수집 (느림)' }}
              </button>
              <div v-if="syncFullResult" class="result-box">
                처리 {{ syncFullResult.postsProcessed }}건 · 신규 {{ syncFullResult.newPosts }}건 · 댓글
                {{ syncFullResult.repliesProcessed }}건 · {{ (syncFullResult.durationMs / 1000).toFixed(1) }}초 소요
              </div>
              <div v-if="syncFullError" class="result-box error">{{ syncFullError }}</div>
            </div>

            <div class="full-sync-row">
              <p class="card-desc small">
                슬랙을 다시 훑지 않고, 이미 저장된 게시글 본문에서 아직 미리보기(제목/썸네일)를 못 가져온
                링크 모음 링크만 다시 시도합니다. 게시글/댓글은 건드리지 않아 빠르고 가볍습니다.
              </p>
              <button class="secondary-btn" :disabled="syncingLinks" @click="runLinkPreviewBackfill">
                {{ syncingLinks ? '링크 미리보기 재수집 중...' : '링크 미리보기 재수집' }}
              </button>
              <div v-if="syncLinksResult" class="result-box">
                {{ syncLinksResult.attempted }}개 링크 재시도 완료
              </div>
              <div v-if="syncLinksError" class="result-box error">{{ syncLinksError }}</div>
            </div>
          </div>

          <div class="sync-failures">
            <div class="sync-failures-title">⚠️ 동기화 실패 목록 ({{ syncFailures.length }}건)</div>
            <p class="card-desc">
              저장에 실패한 게시글입니다. 슬랙 채널에는 알리지 않고 여기서만 보입니다. 원인을 고친 뒤
              "지금 동기화"를 다시 누르면 성공한 항목은 자동으로 목록에서 빠집니다.
            </p>
            <div v-if="syncFailuresLoading" class="status-message">불러오는 중...</div>
            <div v-else-if="syncFailuresError" class="status-message error">{{ syncFailuresError }}</div>
            <div v-else-if="syncFailures.length === 0" class="status-message">현재 실패한 동기화가 없습니다.</div>
            <div v-else class="uncategorized-list">
              <div v-for="failure in syncFailures" :key="failure.slackTs" class="uncategorized-row">
                <div class="manage-header">
                  <span class="row-author">ts: {{ failure.slackTs }}</span>
                  <span class="row-time">{{ formatRelativeTime(failure.failedAt) }}</span>
                </div>
                <div class="row-preview">{{ failure.contentPreview }}</div>
                <div class="row-preview error-text">{{ failure.errorMessage }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section-title">🤖 슬랙 봇 댓글 관리 ({{ botReplies.length }}건)</div>
        <div class="card">
          <p class="card-desc">
            봇이 스레드에 남긴 동기화 안내 댓글입니다. 슬랙에서는 직접 수정·삭제가 안 되니 여기서 관리하세요.
          </p>

          <div class="category-chips">
            <span
              class="chip"
              :class="{ active: botReplyTypeFilter === 'all' }"
              @click="selectBotReplyTypeFilter('all')"
              >전체 {{ botReplies.length }}</span
            >
            <span
              class="chip"
              :class="{ active: botReplyTypeFilter === 'success' }"
              @click="selectBotReplyTypeFilter('success')"
              >✅ 성공 {{ botReplies.filter((r) => r.status === 'success').length }}</span
            >
            <span
              class="chip"
              :class="{ active: botReplyTypeFilter === 'failure' }"
              @click="selectBotReplyTypeFilter('failure')"
              >⚠️ 실패 {{ botReplies.filter((r) => r.status === 'failure').length }}</span
            >
            <span
              class="chip"
              :class="{ active: botReplyTypeFilter === 'pending' }"
              @click="selectBotReplyTypeFilter('pending')"
              >⏳ 대기 {{ botReplies.filter((r) => r.status === 'pending').length }}</span
            >
          </div>
          <div class="category-chips sub-chips">
            <span
              class="chip"
              :class="{ active: botReplyDateFilter === 'all' }"
              @click="selectBotReplyDateFilter('all')"
              >전체 기간</span
            >
            <span
              class="chip"
              :class="{ active: botReplyDateFilter === 'today' }"
              @click="selectBotReplyDateFilter('today')"
              >오늘</span
            >
          </div>

          <div v-if="botReplyActionError" class="result-box error">{{ botReplyActionError }}</div>
          <div v-if="botRepliesLoading" class="status-message">불러오는 중...</div>
          <div v-else-if="botRepliesError" class="status-message error">{{ botRepliesError }}</div>
          <div v-else-if="filteredBotReplies.length === 0" class="status-message">봇 댓글이 없습니다.</div>
          <template v-else>
            <div class="uncategorized-list">
              <div
                v-for="reply in pagedBotReplies"
                :key="reply.id ?? `pending-${reply.postId}`"
                class="uncategorized-row"
              >
                <div class="manage-header">
                  <span class="row-author">{{ reply.postAuthor }} · {{ reply.postPreview }}</span>
                  <span class="row-time">{{ formatRelativeTime(reply.createdAt) }}</span>
                </div>
                <template v-if="editingReplyId === reply.id">
                  <textarea v-model="editingContent" class="tag-input reply-edit-textarea" rows="3"></textarea>
                  <div class="control-row">
                    <button class="save-btn" @click="saveEditReply(reply)">저장</button>
                    <button class="secondary-btn" @click="cancelEditReply">취소</button>
                  </div>
                </template>
                <template v-else>
                  <div class="row-preview">{{ stripSlackMarkdown(reply.content) }}</div>
                  <div class="control-row">
                    <a
                      :href="`/posts/${reply.postId}`"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="secondary-btn link-btn"
                      >게시글 보기</a
                    >
                    <template v-if="reply.status === 'pending'">
                      <button
                        class="save-btn"
                        :disabled="sendingPendingId === reply.postId"
                        @click="sendPending(reply)"
                      >
                        {{ sendingPendingId === reply.postId ? '전송 중...' : '지금 전송' }}
                      </button>
                    </template>
                    <template v-else>
                      <button class="secondary-btn" @click="startEditReply(reply)">수정</button>
                      <button class="secondary-btn" @click="removeBotReply(reply)">삭제</button>
                    </template>
                  </div>
                </template>
              </div>
            </div>
            <div class="control-row bot-reply-pagination">
              <button class="secondary-btn" :disabled="botReplyPage === 0" @click="goToPrevBotReplyPage">이전</button>
              <span>{{ botReplyPage + 1 }} / {{ botReplyTotalPages }}</span>
              <button
                class="secondary-btn"
                :disabled="botReplyPage + 1 >= botReplyTotalPages"
                @click="goToNextBotReplyPage"
              >
                다음
              </button>
            </div>
          </template>
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
            <template v-if="classifyFailedCount > 0">({{ classifyFailedCount }}개는 실패 - 다음에 다시 눌러주세요)</template>
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

        <div class="category-chips sub-chips">
          <span
            class="chip"
            :class="{ active: manageDateFilter === 'all' }"
            @click="selectManageDateFilter('all')"
            >전체 기간</span
          >
          <span
            class="chip"
            :class="{ active: manageDateFilter === 'today' }"
            @click="selectManageDateFilter('today')"
            >오늘</span
          >
        </div>

        <div v-if="manageCategoryTags.length" class="category-chips sub-chips">
          <span
            class="chip"
            :class="{ active: !manageTagFilter }"
            @click="selectManageTag(null)"
            >전체</span
          >
          <span
            v-for="sub in manageCategoryTags"
            :key="sub.value"
            class="chip"
            :class="{ active: manageTagFilter === sub.value }"
            @click="selectManageTag(sub.value)"
            >{{ sub.label }}</span
          >
        </div>

        <div class="bulk-actions">
          <button
            class="primary-btn"
            :disabled="savingAll || allPosts.length === 0"
            @click="saveAllPosts"
          >
            {{ savingAll ? '저장 중...' : `현재 목록 ${allPosts.length}개 전체 저장` }}
          </button>
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
            <div v-if="expandedPostIds.has(post.id)" class="manage-preview manage-preview-full" v-html="renderSlackText(post.content)"></div>
            <div v-else class="manage-preview">{{ managePreview(post.content) }}</div>
            <div
              v-if="stripSlackMarkdown(post.content, { collapseNewlines: false }).length > MANAGE_PREVIEW_LENGTH"
              class="expand-toggle"
              @click="togglePostExpand(post.id)"
            >
              {{ expandedPostIds.has(post.id) ? '접기 ▴' : '더보기 ▾' }}
            </div>

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

        <div v-if="allPosts.length > 0" class="control-row manage-pagination">
          <button class="secondary-btn" :disabled="allPostsPage === 0" @click="goToPrevManagePage">이전</button>
          <span>{{ allPostsPage + 1 }} / {{ Math.max(1, allPostsTotalPages) }}</span>
          <button
            class="secondary-btn"
            :disabled="allPostsPage + 1 >= allPostsTotalPages"
            @click="goToNextManagePage"
          >
            다음
          </button>
        </div>
      </section>

      <section class="section">
        <div class="section-title">📢 공지 관리</div>
        <div class="card">
          <p class="card-desc">여기서 작성한 공지는 저장 즉시 모든 유저의 "전체 공지" 탭에 노출됩니다.</p>

          <div class="category-chips">
            <span
              v-for="preset in ANNOUNCEMENT_PRESETS"
              :key="preset.label"
              class="chip"
              @click="applyAnnouncementPreset(preset)"
              >{{ preset.label }}</span
            >
          </div>

          <div class="control-row">
            <select v-model="newAnnouncement.badgeType" class="category-select">
              <option value="공지">공지</option>
              <option value="버그">버그</option>
              <option value="업데이트">업데이트</option>
            </select>
            <input v-model="newAnnouncement.title" class="tag-input announcement-title-input" placeholder="공지 제목" />
          </div>
          <textarea
            v-model="newAnnouncement.content"
            class="tag-input reply-edit-textarea"
            rows="3"
            placeholder="공지 내용 (선택)"
          ></textarea>

          <p class="card-desc small">이동 경로 (선택) - 공지를 클릭하면 이 경로로 이동합니다</p>
          <div class="category-chips sub-chips">
            <span
              v-for="preset in ANNOUNCEMENT_LINK_PRESETS"
              :key="preset.label"
              class="chip"
              @click="applyAnnouncementLinkPreset(preset)"
              >{{ preset.label }}</span
            >
          </div>
          <div class="control-row">
            <input
              v-model="newAnnouncement.linkPath"
              class="tag-input announcement-title-input"
              placeholder="예: /feed?tab=links"
            />
          </div>

          <div class="control-row">
            <button
              class="primary-btn"
              :disabled="sendingAnnouncement || !newAnnouncement.title.trim()"
              @click="submitAnnouncement"
            >
              {{ sendingAnnouncement ? '저장 중...' : editingAnnouncementId ? '수정 저장' : '보내기' }}
            </button>
            <button v-if="editingAnnouncementId" class="secondary-btn" @click="resetAnnouncementForm">취소</button>
          </div>

          <div v-if="announcementsLoading" class="status-message">불러오는 중...</div>
          <div v-else-if="announcementsError" class="status-message error">{{ announcementsError }}</div>
          <div v-else-if="announcements.length === 0" class="status-message">등록된 공지가 없습니다.</div>
          <div v-else class="uncategorized-list">
            <div v-for="a in announcements" :key="a.id" class="uncategorized-row">
              <div class="manage-header">
                <span class="row-author"
                  >[{{ a.badgeType }}] {{ a.title }} <span v-if="a.updatedAt" class="edited-tag">(수정됨)</span></span
                >
                <span class="row-time">{{ formatRelativeTime(a.createdAt) }}</span>
              </div>
              <div v-if="a.content" class="row-preview">{{ a.content }}</div>
              <div v-if="a.linkPath" class="row-preview">🔗 {{ a.linkPath }}</div>
              <div class="control-row">
                <button class="secondary-btn" @click="startEditAnnouncement(a)">수정</button>
                <button class="secondary-btn" @click="removeAnnouncement(a.id)">삭제</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section-title">💬 문의 · 만족도 응답 확인</div>
        <div class="card">
          <p class="card-desc">문의하기·만족도 조사는 기존에 운영 중인 Tally 폼을 그대로 사용합니다. 응답 확인은 Tally 대시보드에서 하세요.</p>
          <a
            href="https://tally.so/forms/gDX4G4/submissions"
            target="_blank"
            rel="noopener noreferrer"
            class="primary-btn link-btn"
            >Tally 응답함 바로가기 ↗</a
          >
        </div>
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

.full-sync-row {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(26, 26, 46, 0.06);
}

.card-desc.small {
  font-size: 12px;
}

.secondary-btn {
  padding: 9px 18px;
  background: #ffffff;
  color: #636e72;
  border: 1px solid rgba(26, 26, 46, 0.15);
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.secondary-btn:disabled {
  color: #b0b0b0;
  cursor: not-allowed;
}

.secondary-btn:not(:disabled):hover {
  background: #fafafa;
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
  min-width: 0;
  overflow-wrap: break-word;
}

.edited-tag {
  font-weight: 500;
  color: #636e72;
  font-size: 11.5px;
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

.sub-chips {
  margin-top: -6px;
  margin-bottom: 16px;
}

.sub-chips .chip {
  font-size: 11.5px;
  padding: 6px 12px;
  background: #fafafa;
}

.sub-chips .chip.active {
  background: #4a3f8f;
  color: #ffffff;
}

.bulk-actions {
  margin-bottom: 16px;
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
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 12px;
  font-size: 12px;
}

.manage-preview {
  margin-top: 6px;
  font-size: 13.5px;
  color: #1a1a2e;
  white-space: pre-wrap;
  word-break: break-word;
}

.expand-toggle {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #4a3f8f;
  cursor: pointer;
}

.expand-toggle:hover {
  text-decoration: underline;
}

.manage-preview-full :deep(.slack-inline-code) {
  background: #f7e0d9;
  color: #e01e5a;
  font-family: ui-monospace, monospace;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.9em;
}

.manage-preview-full :deep(.slack-codeblock) {
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

.manage-preview-full :deep(.slack-quote) {
  border-left: 3px solid rgba(26, 26, 46, 0.15);
  padding-left: 12px;
  margin: 8px 0;
}

.manage-preview-full :deep(.slack-link) {
  color: #1264a3;
  text-decoration: none;
}

.manage-preview-full :deep(.slack-link:hover) {
  text-decoration: underline;
}

.manage-preview-full :deep(.slack-mention) {
  color: #1264a3;
  background: #e8f5fa;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 600;
}

.sync-layout {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.sync-controls,
.sync-failures {
  flex: 1;
  min-width: 280px;
}

.sync-failures-title {
  font-size: 14px;
  font-weight: 800;
  color: #1a1a2e;
  margin-bottom: 10px;
}

.manage-pagination,
.bot-reply-pagination {
  justify-content: center;
  margin-top: 14px;
  font-size: 12.5px;
  color: #636e72;
}

.link-btn {
  display: inline-block;
  text-decoration: none;
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

.announcement-title-input {
  flex: 1;
  min-width: 200px;
  width: auto;
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

.error-text {
  color: #e01e5a;
  margin-top: 4px;
}

.reply-edit-textarea {
  width: 100%;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(26, 26, 46, 0.1);
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
}

</style>
