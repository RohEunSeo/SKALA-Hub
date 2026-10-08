<script setup>
// AI 챗봇 우측 슬라이드 패널 - 첫 인사(추천 칩) / 대화 / 출처 카드 / 👍👎 피드백 / 남은 횟수.
// 저장·폴더: 서버가 save_proposal 을 보내면 입력창 위에 "폴더에 저장할까요?" 카드가 뜬다.
// 공유 드라이브 push 는 아직 범위 밖이라 SHOW_PUSH_OFFER 로 꺼둔 상태다.
// 지금은 api/chat.js의 가짜 응답으로 동작하는 화면 목업 (서버 연동 전)
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { CARD_DELAY, CARD_STEP, useChatStore } from '../../stores/chat'
import { useAuthStore } from '../../stores/auth'
import { usePostsStore } from '../../stores/posts'
import { useMyPageStore } from '../../stores/mypage'
import { useBookmarksStore } from '../../stores/bookmarks'
import { useToastStore } from '../../stores/toast'
import { useUiStore } from '../../stores/ui'
import { CATEGORIES } from '../../constants/categories'
import { folderTextColor } from '../../utils/folderColors'
import { inlineMarkdown } from '../../utils/miniMarkdown'
import skalaIcon from '../../assets/skala_icon.png'
import SproutLoader from './SproutLoader.vue'
import PaperFlipLoader from './PaperFlipLoader.vue'
import SaveLoader from './SaveLoader.vue'
import FolderIcon from '../FolderIcon.vue'
import { FOLDER_COLORS, useFoldersStore } from '../../stores/folders'

const chatStore = useChatStore()
const bookmarksStore = useBookmarksStore()
const toastStore = useToastStore()

// 추천 카드에서 바로 저장. 카드 전체가 '글 보기' 버튼이라 전파를 막아야 한다.
const isSaved = (id) => bookmarksStore.bookmarkedPostIds.includes(id)
async function toggleSave(id) {
  const was = isSaved(id)
  try {
    await bookmarksStore.toggle(id)
    toastStore.show(was ? '저장을 취소했습니다' : '저장했어요. 마이페이지에서 볼 수 있어요')
  } catch {
    toastStore.show('저장에 실패했습니다. 잠시 후 다시 시도해주세요.')
  }
}
const authStore = useAuthStore()
const postsStore = usePostsStore()
const myPageStore = useMyPageStore()
const uiStore = useUiStore()
const router = useRouter()
const route = useRoute()

const input = ref('')
const bodyEl = ref(null)

// 지금 보고 있는 피드 카테고리 - "공유 중" 표시 + 서버로 넘겨 검색 범위 힌트로 사용
const context = computed(() => {
  const cat = CATEGORIES.find((c) => c.value === postsStore.category)
  return cat ? `${cat.label} 피드` : '전체 피드'
})

// 상세 페이지(/posts/:id)에 있으면 그 글. 아니면 null.
// 제목은 피드 목록에서 찾는다 - 추천 카드를 눌러 들어온 경우엔 항상 있다.
// 주소를 직접 입력해 들어오면 제목만 비는데, 그때도 요약은 된다(서버가 글을 읽으므로).
const openPost = computed(() => {
  if (route.name !== 'post-detail') return null
  const id = Number(route.params.id)
  if (!id) return null
  return { id, title: postsStore.posts.find((p) => p.id === id)?.aiTitle ?? '' }
})

// 탭 안에 있으면 그 탭 정보, 전체 피드면 null.
// 챗봇은 보고 있는 탭 안에서만 찾는데, 그걸 모르면 "맥북 글이 왜 안 나오지?"가 된다.
// (학습자료 탭에서 점심 질문을 하면 실제로 거절된다 - 실측)
const scope = computed(() => CATEGORIES.find((c) => c.value === postsStore.category) ?? null)

const userName = computed(() => authStore.user?.name ?? '')
const isEmpty = computed(() => chatStore.messages.length === 0)

// 상세 페이지에선 칩이 통째로 바뀐다.
// 안내 문구로 "상세 페이지에선 요약도 됩니다"라고 설명하는 대신, 버튼이 바뀌어 있으면 된다.
// 상세 페이지에서 쓰는 문구. 첫 화면 칩과 입력창 위 버튼이 같은 값을 보게 한 곳에 둔다.
const POST_ACTIONS = ['이 글의 핵심 요약해줘', '비슷한 글 찾아줘']

const chips = computed(() =>
  openPost.value ? POST_ACTIONS : (chatStore.greeting?.chips ?? []),
)
// 답변이 아직 비어 있는 마지막 AI 메시지에만 로더를 보여줌
const lastId = computed(() => chatStore.messages[chatStore.messages.length - 1]?.id)

// ⋮ 메뉴 - 대화 종료하기 + 이전 대화 목록 (기본 3개, 더보기로 전체)
const menuOpen = ref(false)
const showAllHistory = ref(false)
const shownHistory = computed(() => (showAllHistory.value ? chatStore.history : chatStore.history.slice(0, 3)))

function menuAction(fn) {
  fn()
  menuOpen.value = false
  showAllHistory.value = false
}

// 왼쪽 가장자리 드래그로 패널 폭 조절 (포인터 캡처로 마우스가 벗어나도 계속 추적)
function startResize(e) {
  chatStore.dragging = true
  e.currentTarget.setPointerCapture(e.pointerId)
}
function onResize(e) {
  if (chatStore.dragging) chatStore.setWidth(window.innerWidth - e.clientX)
}
function endResize() {
  chatStore.dragging = false
}

// 저장 완료 후 본문 이동 - 폴더에 담았으면 피드의 AI 추천 탭 그 폴더, 아니면 마이페이지 저장한 글 (챗봇 패널은 열린 채 유지)
function goSaved(proposal) {
  if (proposal?.folderId) {
    useFoldersStore().selected = proposal.folderId
    return goPicks()
  }
  if (route.path === '/mypage') myPageStore.setTab('saved')
  else router.push('/mypage?tab=saved')
}

// 폴더에 담긴 글 보기 - 추천 결과 자체는 게시글 탭에서 이미 하이라이트돼 있다
function goPicks() {
  if (route.path === '/feed') uiStore.feedTab = 'posts'
  else router.push('/feed')
}

// 추천된 글이 하이라이트된 게시글 목록으로 이동
function goFeed() {
  if (route.path !== '/feed') router.push('/feed')
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 추천 카드 → 그 글 상세로. 챗봇 패널은 열어 둔 채 왼쪽 본문만 바뀐다.
// 뒤로가기로 돌아오면 추천 상태(postsStore.pickedIds)가 스토어에 남아 있어 그대로 복원되고,
// 라우터의 scrollBehavior가 보던 위치까지 되돌려 준다.
//
// 이미 상세를 보고 있으면 push가 아니라 **replace**로 바꾼다.
// 패널에서 카드를 옮겨 누르는 건 "더 깊이 들어가는 것"이 아니라 "옆으로 옮겨 보는 것"이라,
// 방문한 글마다 기록이 쌓이면 뒤로가기를 여러 번 눌러야 피드로 돌아온다.
function goPost(id) {
  const to = { name: 'post-detail', params: { id } }
  if (route.name === 'post-detail') router.replace(to)
  else router.push(to)
}

// 추천 글의 카테고리 → 이모지/색 (categories.js가 원본)
const catOf = (v) => CATEGORIES.find((c) => c.value === v || c.label === v) ?? { icon: '📄', label: v, color: '#6c5ce7' }

// 페이지 이동으로 패널이 다시 만들어질 때 카드 등장 모션이 반복되지 않게, 떠날 때 본 것으로 표시
onBeforeUnmount(() => chatStore.messages.forEach((m) => (m.seen = true)))

// 저장 중 폴더 로더 색 - 선택한 폴더의 색 (스토어에 색이 없어도 폴더 이름으로 찾음), 폴더 없이 저장이면 기본 보라
const foldersStore = useFoldersStore()
const savingColor = (proposal) =>
  proposal.folderColor ?? foldersStore.folders.find((f) => f.name === proposal.folder)?.color ?? undefined

// 새 폴더 만들기 카드 - 이름/색 입력 (카드가 뜰 때마다 초기화)
const folderName = ref('')
const folderColor = ref(FOLDER_COLORS[0])
watch(
  () => chatStore.pending,
  (p) => {
    if (p?.form) {
      folderName.value = ''
      folderColor.value = p.defaultColor
    }
  },
)
function submitFolder() {
  if (folderName.value.trim()) chatStore.submitForm({ name: folderName.value, color: folderColor.value })
}

// '직접 입력' 선택 - 카드를 닫고 입력창으로 포커스
const inputEl = ref(null)
function pickOther() {
  chatStore.dismiss()
  inputEl.value?.focus()
}

// 답변 복사 - 잠깐 ✓로 바뀜
const copiedId = ref(null)
async function copyAnswer(msg) {
  try {
    await navigator.clipboard.writeText(msg.text)
    copiedId.value = msg.id
    setTimeout(() => (copiedId.value = null), 1500)
  } catch { /* 클립보드 권한이 없으면 무시 */ }
}

// 아쉬운 이유. 고장 난 곳이 서로 달라서 이렇게 나눈다 -
// '관련 없는 글' = 검색 정확도, '찾던 글이 없다' = 검색 누락, '내용이 틀리다' = 생성.
// 로그를 볼 때 이 셋이 섞여 있으면 무엇부터 고칠지 못 정한다.
const REASONS = ['관련 없는 글이 섞여 있어요', '찾던 글이 없어요', '답변 내용이 틀려요']

// 좋아요가 아니고 아직 이유를 안 골랐으면 이유를 묻는다
const otherFor = ref(null)
const otherText = ref('')
const otherInput = ref(null)

const needsReason = (msg) =>
  msg.feedback && msg.feedback.value !== 'up' && !msg.feedback.reason && otherFor.value !== msg.id

// 사용법 안내는 추천이 처음 나왔을 때만. 매번 띄우면 저장 제안·평가와 겹쳐 아무것도 안 읽힌다.
// 실제로 카드를 '누를 수 있다'는 걸 몰라서 상세로 들어가 볼 생각을 못 한다는 피드백을 받았다.
const firstSourcesId = computed(() => chatStore.messages.find((m) => m.sources?.length)?.id ?? null)

async function startOther(msg) {
  otherFor.value = msg.id
  otherText.value = ''
  await nextTick()
  otherInput.value?.focus?.()
}

function submitOther(msg) {
  const reason = otherText.value.trim()
  if (!reason) return
  chatStore.setFeedback(msg, msg.feedback.value, reason)
  otherFor.value = null
  otherText.value = ''
}

function send(text = input.value) {
  if (!text.trim()) return
  input.value = ''
  // 라벨(표시용)과 카테고리 값(검색 범위 제한용)을 함께 넘긴다.
  // 전체 피드에서 물으면 전체에서, 카테고리 피드에서 물으면 그 안에서만 찾는다.
  // 상세 페이지면 글 번호도 넘긴다. 서버는 이게 있을 때만 요약/비슷한글을 처리한다.
  chatStore.ask(text, context.value, postsStore.category, null, openPost.value?.id ?? null)
}

function onKeydown(e) {
  // 한글 조합 중 Enter는 무시 (마지막 글자가 두 번 전송되는 문제 방지)
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

// 새 글자/메시지가 붙을 때마다 맨 아래로 스크롤
watch(
  () => [chatStore.messages.length, chatStore.messages[chatStore.messages.length - 1]?.text, chatStore.status],
  async () => {
    await nextTick()
    if (bodyEl.value) bodyEl.value.scrollTop = bodyEl.value.scrollHeight
  },
  { deep: true },
)
</script>

<template>
  <aside class="chat-panel" :class="{ open: chatStore.isOpen }" :style="{ '--chat-w': `${chatStore.width}px` }" aria-label="AI 도우미" :aria-hidden="!chatStore.isOpen">
    <!-- 왼쪽 가장자리: 드래그로 폭 조절 + 가운데 » 버튼으로 접기 -->
    <div class="cp-resize" :class="{ dragging: chatStore.dragging }" @pointerdown="startResize" @pointermove="onResize" @pointerup="endResize" @pointercancel="endResize"></div>
    <button class="cp-fold" aria-label="챗봇 접기" @click="chatStore.close">»</button>

    <header class="cp-head">
      <!-- 헤더 왼쪽 표식 (제목 글자는 없음) -->
      <button class="cp-sprout" aria-label="처음 화면으로" title="처음 화면으로" @click="chatStore.endConversation">🌱</button>
      <div class="cp-actions">
        <button class="cp-more" aria-label="메뉴" aria-haspopup="menu" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><circle cx="12" cy="4.5" r="2.4" /><circle cx="12" cy="12" r="2.4" /><circle cx="12" cy="19.5" r="2.4" /></svg>
        </button>
        <button class="cp-close" aria-label="닫기" @click="chatStore.close">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M5 5l14 14M19 5L5 19" /></svg>
        </button>
      </div>

      <!-- 바깥 클릭하면 메뉴 닫힘 -->
      <div v-if="menuOpen" class="cp-backdrop" @click="menuOpen = false"></div>
      <div v-if="menuOpen" class="cp-menu" role="menu">
        <button class="cm-item" role="menuitem" :disabled="isEmpty || !!chatStore.status" @click="menuAction(chatStore.endConversation)">
          💬 대화 종료하기
        </button>
        <div class="cm-sep"></div>
        <div class="cm-label">이전 대화</div>
        <p v-if="!chatStore.history.length" class="cm-empty">아직 이전 대화가 없어요</p>
        <button
          v-for="h in shownHistory"
          :key="h.id"
          class="cm-item cm-history"
          role="menuitem"
          :disabled="!!chatStore.status"
          @click="menuAction(() => chatStore.openConversation(h.id))"
        >
          <span class="cm-bars">≡</span><span class="cm-title">{{ h.title }}</span>
        </button>
        <button v-if="!showAllHistory && chatStore.history.length > 3" class="cm-item cm-more" @click="showAllHistory = true">
          ⋯ 이전 대화 더보기 ({{ chatStore.history.length - 3 }})
        </button>
      </div>
    </header>

    <div ref="bodyEl" class="cp-body">
      <!-- 첫 인사: LLM 없이 DB 집계로 만든 문구 + 추천 칩 (횟수 차감 없음) -->
      <div v-if="isEmpty" class="cp-empty">
        <div class="cp-hero">
<!-- 허브: 폴더를 중심으로 점들이 천천히 도는 궤도 (스포크형 허브 느낌) + 문서를 한 장씩 천천히 넘김 -->
          <div class="cp-hub" aria-hidden="true">
            <span class="hub-ring"></span>
            <span class="hub-orbit"><i></i><i></i><i></i></span>
            <PaperFlipLoader :width="52" :duration="5.4" />
          </div>
          <h2>{{ userName ? `${userName}님, ` : '' }}무엇이 궁금하세요?</h2>
          <p v-if="chatStore.greeting" class="cp-greeting">{{ chatStore.greeting.text }}</p>
          <!-- 할 수 있는 일. 가운데 정렬하면 양 끝이 들쭉날쭉해 안 읽히므로 왼쪽으로 맞춘다 -->
          <ul v-if="chatStore.greeting?.items" class="cp-help">
            <li v-for="(it, i) in chatStore.greeting.items" :key="i">
              <span class="cp-help-icon" aria-hidden="true">{{ it.icon }}</span>
              <span><b>{{ it.strong }}</b> {{ it.rest }}</span>
            </li>
          </ul>
          <!-- 지금 보고 있는 화면을 알려준다. 전체 피드면 안 띄운다(설명할 게 없다) -->
          <p v-if="openPost" class="cp-scope">
            <span class="cp-scope-tag">📄 {{ openPost.title || '이 글' }}</span>
            을 보고 있어요
          </p>
          <p v-else-if="scope" class="cp-scope">
            <span class="cp-scope-tag">{{ scope.icon }} {{ scope.label }}</span>
            안에서만 찾고 있어요 · 없으면 전체에서도 찾아드릴게요
          </p>
        </div>
        <div v-if="chips.length" class="cp-chips">
          <button v-for="chip in chips" :key="chip" class="cp-chip" @click="send(chip)">{{ chip }}</button>
        </div>
      </div>

      <template v-for="msg in chatStore.messages" :key="msg.id">
        <div v-if="msg.role === 'user'" class="bubble user">{{ msg.text }}</div>

        <div v-else class="ai-block">
          <!-- 진행 표시: 폴더 모션 + 한 줄 문구가 단계마다 바뀌고 경과 시간(초) 표시 (저장 중과 동일한 디자인) -->
          <div v-if="!msg.text && chatStore.status && msg.id === lastId" class="bubble ai loading">
            <!-- 단계마다 다른 모션: 의도 분석=레이더, 검색=폴더에서 문서, 고민=새싹, 답변 정리=글줄 -->
            <span class="lo-icon">
              <PaperFlipLoader v-if="chatStore.status.tool === 'search_posts'" :width="52" :duration="3.6" />
              <SproutLoader v-else-if="chatStore.status.tool === 'think'" :size="34" />
              <span v-else-if="chatStore.status.tool === 'compose'" class="lo-lines"><i></i><i></i><i></i></span>
              <span v-else class="lo-radar"><i></i><i></i></span>
            </span>
            <span class="dots"><i></i><i></i><i></i></span>
            <span :key="chatStore.status.text" class="swap"><span class="think">{{ chatStore.status.text }}</span></span>
            <span class="secs">{{ chatStore.elapsed }}초</span>
          </div>

          <div v-if="msg.text" class="answer" :class="{ error: msg.error }" v-html="inlineMarkdown(msg.text)"></div>

          <!-- 출처 카드: 답변의 근거가 된 글 -->
          <!-- 키워드 집계 결과: 누르면 그 키워드로 바로 검색 -->
          <div v-if="msg.keywords" class="kw-row">
            <button v-for="k in msg.keywords" :key="k.word" class="kw" @click="send(`'${k.word}' 관련 글 추천해줘`)">
              # {{ k.word }}<span>{{ k.count }}</span>
            </button>
          </div>

          <!-- 추천 글: 폴더 입구(위쪽 띠) 뒤에서 카드가 차례로 미끄러져 나옴 -->
          <div v-if="msg.sources" class="results">
            <article
              v-for="(p, i) in msg.sources"
              :key="p.id"
              class="source-card clickable"
              :class="{ saved: p.saved, enter: !msg.seen }"
              :style="{ '--delay': `${CARD_DELAY + i * CARD_STEP}ms` }"
              role="button"
              tabindex="0"
              :aria-label="`${p.title} 게시글 보기`"
              @click="goPost(p.id)"
              @keydown.enter.prevent="goPost(p.id)"
              @keydown.space.prevent="goPost(p.id)"
            >
              <div class="sc-main">
                <strong>{{ p.title }}</strong>
                <div class="sc-meta">
                  <span class="sc-chip" :style="{ background: `color-mix(in srgb, ${catOf(p.category).color} 16%, #fff)`, color: folderTextColor(catOf(p.category).color) }">{{ catOf(p.category).icon }} {{ catOf(p.category).label }}</span>
                  <span class="sc-react">반응 {{ p.reactions }}</span>
                </div>
              </div>
              <!-- 목업 때 만든 .sc-save 디자인을 그대로 쓴다 (알약 + 글자) -->
              <button
                class="sc-save"
                :class="{ saved: isSaved(p.id) }"
                :aria-label="isSaved(p.id) ? '저장 취소' : '저장하기'"
                :aria-pressed="isSaved(p.id)"
                @click.stop="toggleSave(p.id)"
                @keydown.enter.stop
                @keydown.space.stop
              >{{ isSaved(p.id) ? '저장됨' : '저장' }}</button>
            </article>
            <!-- 왼쪽 아래 작은 폴더 - 카드가 여기서 위로 펼쳐져 나옴 / 오른쪽은 피드 AI 추천 탭 링크 -->
            <div class="results-foot">
              <div class="results-folder" aria-hidden="true">
                <PaperFlipLoader :width="54" still />
              </div>
              <button v-if="chatStore.aiPicks.length" class="goto-saved" @click="goFeed">피드에서 보기</button>
            </div>
          </div>

          <!-- 추천 바로 아래에서 평가를 받는다. 답변 맨 끝 작은 아이콘 행에 두면 눈에 안 띄어
               아무도 누르지 않는다(로그 104건 중 피드백 1건). 글자를 붙여 크게 둔다. -->
          <p v-if="msg.sources?.length && msg.id === firstSourcesId" class="hint">
            💡 <b>카드를 누르면</b> 글로 이동해요. 거기서 <b>요약</b>이나 <b>비슷한 글</b>도 물어볼 수 있어요.
          </p>

          <!-- 저장 제안: AI는 제안만 하고, 실제 저장은 사용자가 버튼을 눌러야 함 -->
          <template v-if="msg.proposal">
            <div v-if="msg.proposal.state === 'saving'" class="bubble ai loading">
              <span class="lo-icon"><SaveLoader :width="52" :color="savingColor(msg.proposal)" /></span>
              <span class="dots"><i></i><i></i><i></i></span>
              <span class="think">{{ msg.proposal.folder ? `'${msg.proposal.folder}' 폴더에 ` : '' }}저장하는 중</span>
            </div>
            <div v-else-if="msg.proposal.state === 'saved'" class="answer saved-note">
              {{ msg.proposal.folder ? `'${msg.proposal.folder}' 폴더에 ` : '' }}{{ msg.proposal.postIds.length }}개 저장했어요. {{ msg.proposal.folderId ? '피드의 AI 추천 탭에서 폴더별로 볼 수 있어요.' : '마이페이지 → 저장한 글에서 확인할 수 있어요.' }}
              <button class="goto-saved" @click="goSaved(msg.proposal)">{{ msg.proposal.folderId ? '폴더 보러가기 →' : '저장한 글 보러가기 →' }}</button>
            </div>
            <div v-else-if="msg.proposal.state === 'declined'" class="answer">알겠어요. 저장하지 않을게요.</div>
          </template>

          <!-- 공유 드라이브 push: 저장 후 폴더를 공유 레포로 올릴지 제안 (사용자가 네를 눌러야 실행) -->
          <template v-if="msg.push">
            <div v-if="msg.push.state === 'pushing'" class="bubble ai loading">
              <span class="lo-icon"><SaveLoader :width="52" :color="msg.push.color" /></span>
              <span class="dots"><i></i><i></i><i></i></span>
              <span class="think">'{{ msg.push.folder }}' 공유 드라이브에 push하는 중</span>
            </div>
            <div v-else-if="msg.push.state === 'pushed'" class="answer saved-note">
              '{{ msg.push.folder }}' 폴더를 공유 드라이브에 push했어요. 다른 교육생이 star하고 clone할 수 있어요.
              <button class="goto-saved" @click="router.push(`/community/drive/${encodeURIComponent(msg.push.owner)}/${encodeURIComponent(msg.push.folder)}`)">레포 보러가기 →</button>
            </div>
            <div v-else-if="msg.push.state === 'declined'" class="answer">알겠어요. 나중에 레포 화면에서 push할 수도 있어요.</div>
          </template>

          <div v-if="msg.sources && !(chatStore.status && msg.id === lastId)" class="rate bare">
            <template v-if="!msg.feedback">
              <span class="rate-q">이 추천 어떠셨나요?</span>
              <button class="rate-ico" aria-label="좋아요" @click="chatStore.setFeedback(msg, 'up')">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 10v12M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z" /></svg>좋아요
              </button>
              <button class="rate-ico" aria-label="보통이에요" @click="chatStore.setFeedback(msg, 'mid')">
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M8.5 15h7" /><path d="M9 9.5h.01M15 9.5h.01" /></svg>보통
              </button>
              <button class="rate-ico" aria-label="별로예요" @click="chatStore.setFeedback(msg, 'down')">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M17 14V2M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z" /></svg>별로예요
              </button>
            </template>
            <!-- 좋아요가 아니면 왜 그런지 묻는다. 이유가 어디가 고장났는지를 가린다 -->
            <template v-else-if="needsReason(msg)">
              <span class="rate-q">어떤 점이 아쉬웠나요?</span>
              <button v-for="r in REASONS" :key="r" class="rate-btn" @click="chatStore.setFeedback(msg, msg.feedback.value, r)">{{ r }}</button>
              <button class="rate-btn" @click="startOther(msg)">✎ 직접 쓰기</button>
            </template>
            <template v-else-if="otherFor === msg.id">
              <input
                ref="otherInput"
                v-model="otherText"
                class="rate-input"
                maxlength="200"
                placeholder="어떤 점이 아쉬웠는지 적어주세요"
                aria-label="아쉬운 점"
                @keydown.enter.prevent="submitOther(msg)"
              />
              <button class="rate-btn" :disabled="!otherText.trim()" @click="submitOther(msg)">보내기</button>
            </template>
            <span v-else class="rate-thanks">의견 감사합니다 🙏</span>
          </div>

          <!-- 답변 아래 아이콘 행: 복사만 -->
          <div v-if="msg.text && !msg.error && !(chatStore.status && msg.id === lastId)" class="actions">
            <button class="act" aria-label="답변 복사" @click="copyAnswer(msg)">
              <svg v-if="copiedId === msg.id" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12l5 5L19 7" /></svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true"><rect width="14" height="14" x="8" y="8" rx="2" /><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" /></svg>
            </button>
          </div>
        </div>
      </template>
    </div>

    <footer class="cp-foot">
      <!-- AI가 사용자에게 되묻는 카드 (저장 여부, 모호한 질문의 선택지) - 입력창 바로 위 -->
      <div v-if="chatStore.pending" class="cp-ask" role="group" :aria-label="chatStore.pending.question">
        <div class="ask-head">
          <span class="ask-q">{{ chatStore.pending.question }}</span>
          <button class="ask-skip" @click="chatStore.dismiss">건너뛰기</button>
        </div>
        <div v-if="chatStore.pending.form" class="ask-form">
          <input v-model="folderName" maxlength="20" placeholder="예: SQLD 자료" aria-label="폴더 이름" @keydown.enter="submitFolder" />
          <div class="swatches">
            <button
              v-for="c in FOLDER_COLORS"
              :key="c"
              class="swatch"
              :class="{ on: folderColor === c }"
              :style="{ background: c }"
              :aria-label="`폴더 색 ${c}`"
              @click="folderColor = c"
            ></button>
          </div>
          <button class="ask-submit" :disabled="!folderName.trim()" @click="submitFolder">만들고 저장하기</button>
        </div>
        <button v-for="(o, i) in chatStore.pending.options" :key="o.value" class="ask-opt" @click="chatStore.answer(o)">
          <FolderIcon v-if="o.color" class="ask-folder" :color="o.color" :size="22" />
          <span v-else class="ask-n">{{ o.plus ? '＋' : i + 1 }}</span>{{ o.label }}
        </button>
        <button v-if="chatStore.pending.allowOther" class="ask-opt" @click="pickOther">
          <span class="ask-n">✎</span>직접 입력할게요
        </button>
      </div>
      <!-- 상세 페이지 전용 빠른 버튼. 첫 화면 칩은 대화가 시작되면 사라지는데,
           추천 카드를 눌러 들어오면 이미 대화가 있어서 칩을 못 본다. 그래서 여기 따로 둔다. -->
      <div v-if="openPost && !chatStore.pending" class="cp-quick">
        <button v-for="q in POST_ACTIONS" :key="q" class="cp-quick-btn" @click="send(q)">{{ q }}</button>
      </div>
      <div class="cp-usage">오늘 {{ chatStore.remaining }}/{{ chatStore.limit }}회 남음</div>
      <div class="cp-input">
        <div class="cp-context"><img class="cp-logo" :src="skalaIcon" alt="SKALA" />{{ openPost ? `"${openPost.title || '이 글'}" 글 보는 중` : `"${context}" 탭 공유 중` }}</div>
        <textarea
          ref="inputEl"
          v-model="input"
          rows="3"
          maxlength="500"
          :placeholder="chatStore.remaining > 0 ? '관심 있는 주제를 입력하세요...' : '오늘 횟수를 모두 사용했어요'"
          :disabled="chatStore.remaining <= 0"
          aria-label="질문 입력"
          @keydown="onKeydown"
        ></textarea>
        <button class="cp-send" :disabled="!input.trim() || !!chatStore.status || chatStore.remaining <= 0" aria-label="보내기" @click="send()">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7" /></svg>
        </button>
      </div>
    </footer>
  </aside>
</template>

<style scoped>
.chat-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 90;
  width: var(--chat-w, 420px);
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #ffffff 0%, #f1eefc 55%, #e2dcf7 100%);
  border-left: 1px solid rgba(74, 63, 143, 0.12);
  box-shadow: -8px 0 24px rgba(26, 26, 46, 0.08);
  transform: translateX(100%);
  visibility: hidden;
  transition: transform 0.32s cubic-bezier(0.2, 0.8, 0.3, 1), visibility 0s linear 0.32s;
}

.chat-panel.open {
  transform: translateX(0);
  visibility: visible;
  transition-delay: 0s;
}

.cp-head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
}

.cp-sprout {
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transition: transform 0.15s;
}

.cp-sprout:hover {
  transform: scale(1.15) rotate(-6deg);
}

.cp-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cp-more {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: transparent;
  color: #2d2d44;
  cursor: pointer;
}

.cp-more:hover {
  background: rgba(74, 63, 143, 0.08);
}

.cp-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1;
}

.cp-menu {
  position: absolute;
  top: 54px;
  left: 18px;
  right: 18px;
  z-index: 2;
  padding: 8px 0;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 8px 28px rgba(26, 26, 46, 0.18);
  animation: fade-up 0.18s ease-out both;
}

.cm-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 20px;
  border: 0;
  background: transparent;
  color: #1a1a2e;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.cm-item:hover:not(:disabled) {
  background: #f4f2fc;
}

.cm-item:disabled {
  color: #b2b7ba;
  cursor: default;
}

.cm-sep {
  height: 1px;
  margin: 6px 0;
  background: #ececf3;
}

.cm-label {
  padding: 6px 20px 2px;
  font-size: 12px;
  font-weight: 700;
  color: #636e72;
}

.cm-empty {
  margin: 0;
  padding: 8px 20px 10px;
  font-size: 13px;
  color: #b2b7ba;
}

.cm-bars {
  color: #b2b7ba;
}

.cm-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cm-more {
  color: #636e72;
}

.cp-close {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid rgba(74, 63, 143, 0.15);
  display: grid;
  place-items: center;
  background: #ffffff;
  color: #2d2d44;
  cursor: pointer;
}

.cp-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cp-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  text-align: center;
}

.cp-hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.cp-hub {
  position: relative;
  display: grid;
  place-items: center;
  width: 108px;
  height: 108px;
  margin-bottom: 6px;
}

.hub-ring {
  position: absolute;
  inset: 0;
  border: 1.5px dashed #cfc9f3;
  border-radius: 50%;
}

.hub-orbit {
  position: absolute;
  inset: 0;
  animation: hub-spin 16s linear infinite;
}

.hub-orbit i {
  position: absolute;
  width: 12px;
  height: 12px;
  margin: -6px;
  border-radius: 50%;
  background: #6c5ce7;
  box-shadow: 0 0 0 4px rgba(108, 92, 231, 0.15);
}

/* 3개 점을 궤도(반지름 64px) 위에 120도 간격으로 배치 */
.hub-orbit i:nth-child(1) { left: 50%; top: 0; }
.hub-orbit i:nth-child(2) { left: 93.3%; top: 75%; background: #8b7ee8; }
.hub-orbit i:nth-child(3) { left: 6.7%; top: 75%; background: #b2a9e3; }

@keyframes hub-spin {
  to { transform: rotate(360deg); }
}

.cp-empty h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 400;
  line-height: 1.35;
  color: #1a1a2e;
}

.cp-scope {
  margin: 10px 0 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: #8a86a0;
  display: flex;
  gap: 5px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
}

.cp-scope-tag {
  max-width: 230px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 9px;
  border-radius: 999px;
  background: #efedf8;
  color: #4a3f8f;
  font-weight: 600;
  white-space: nowrap;
}

.cp-greeting {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #636e72;
  max-width: 100%;
  white-space: pre-line; /* 문구 안의 줄바꿈(\n)을 그대로 두 줄로 표시 */
}

.cp-help {
  margin: 4px 0 0;
  padding: 12px 14px;
  list-style: none;
  display: grid;
  gap: 9px;
  width: 100%;
  max-width: 320px;
  border-radius: 12px;
  background: #f7f5fd;
  text-align: left;   /* .cp-empty 의 가운데 정렬을 여기서만 되돌린다 */
}

.cp-help li {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 8px;
  align-items: start;
  font-size: 12.5px;
  line-height: 1.55;
  color: #636e72;
}

.cp-help-icon {
  line-height: 1.55;
}

.cp-help b {
  color: #4a3f8f;
  font-weight: 700;
}

.cp-chips {
  display: flex;
  flex-direction: column;
  align-items: flex-start; /* 글자 길이만큼만 박스가 생김 */
  gap: 10px;
  padding: 0 0 18px;
}

.cp-chip {
  padding: 13px 20px;
  border: 0;
  border-radius: 999px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 15px;
  font-weight: 600;
  text-align: left;
  max-width: 100%;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(26, 26, 46, 0.06);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.cp-chip:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(74, 63, 143, 0.15);
}

.bubble {
  max-width: 88%;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble.user {
  align-self: flex-end;
  background: #4a3f8f;
  color: #ffffff;
  border-bottom-right-radius: 4px;
  animation: fade-up 0.25s ease-out both;
}

.bubble.ai {
  background: #ffffff;
  color: #1a1a2e;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(26, 26, 46, 0.06);
  width: fit-content;
}

.bubble.ai.error {
  background: #fdeef1;
  color: #b3163f;
}

.bubble.loading,
.bubble.ai.loading {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0; /* 말풍선 배경 없이 글자만 */
  background: none;
  box-shadow: none;
  color: #636e72;
}

.ai-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  animation: fade-up 0.25s ease-out both;
}

/* 추천 글 카드 - 위쪽 폴더 띠 뒤에서 카드가 하나씩 나오는 모션 */
.kw-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.kw {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border: 1px solid #e6e2f6;
  border-radius: 999px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 14px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.kw span {
  color: #9aa3a7;
  font-size: 12px;
}

.kw:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(74, 63, 143, 0.15);
}

.results {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 2px 10px;
  padding-top: 10px;
  overflow: hidden; /* 아래로 내려간 카드가 폴더 밖으로 보이지 않게 */
}

.results-folder {
  position: relative;
  z-index: 2; /* 카드보다 앞 */
  width: 54px;
  flex: none;
}

.results-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
}

.goto-saved {
  display: block;
  margin-top: 8px;
  padding: 0;
  border: 0;
  background: none;
  color: #4a3f8f;
  font-size: 14px;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.results-foot .goto-saved {
  margin-top: 0;
}

.source-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e6e2f6;
  box-shadow: 0 1px 3px rgba(26, 26, 46, 0.05);
  position: relative;
  transform-origin: 6% 100%; /* 왼쪽 아래 폴더에서 펼쳐지는 느낌 */
}

/* 등장 모션은 처음 나타날 때(.enter)만 */
.source-card.enter {
  animation: card-out 0.62s cubic-bezier(0.22, 1, 0.36, 1) var(--delay, 0s) both;
}

.source-card:last-of-type {
  border-bottom-left-radius: 4px; /* 맨 아래 카드만 말풍선처럼 - 꼬리가 폴더를 가리킴 */
}

.source-card:last-of-type::after {
  content: '';
  position: absolute;
  left: 20px;
  bottom: -7px;
  width: 12px;
  height: 12px;
  background: inherit;
  border: inherit;
  border-top: 0;
  border-left: 0;
  transform: rotate(45deg);
}

/* 누르면 그 글 상세로 간다는 신호 - 커서와 아주 작은 들림만. 추천 카드가 이미 애니메이션을
   갖고 있어서 호버까지 요란하면 시선이 분산된다 */
.source-card.clickable {
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.source-card.clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(74, 63, 143, 0.14);
}

.source-card.clickable:focus-visible {
  outline: 2px solid #6c5ce7;
  outline-offset: 2px;
}

.source-card.saved {
  border-color: #cfc9f3;
  background: #faf9ff;
}

.source-card.enter.saved {
  animation: filed 0.4s ease-out 0s;
}

.sc-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  font-size: 14px;
  line-height: 1.4;
}

.sc-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sc-chip {
  padding: 2px 8px;
  border-radius: 999px;
  background: #ece9fb;
  color: #4a3f8f;
  font-size: 12px;
}

.sc-react {
  font-size: 12px;
  color: #636e72;
}

.sc-save {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 6px 12px;
  border: 1px solid #cfc9f3;
  border-radius: 999px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.sc-save:hover {
  background: #f4f2fc;
}

.sc-save.saved {
  border-color: transparent;
  background: #ece9fb;
  animation: pop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.sc-check path {
  stroke-dasharray: 24;
  stroke-dashoffset: 24;
  animation: draw 0.35s 0.1s ease-out forwards;
}

.saved-note {
  animation: pop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

.answer {
  font-size: 15px;
  line-height: 1.7;
  color: #1a1a2e;
  white-space: pre-wrap;
  word-break: break-word;
}

.answer strong {
  font-weight: 700;
  color: #4a3f8f;
}

.answer code {
  padding: 1px 5px;
  border-radius: 4px;
  background: #f7e0d9;
  color: #e01e5a;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.answer.error {
  padding: 10px 14px;
  border-radius: 12px;
  background: #fdeef1;
  color: #b3163f;
}

.rate {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f7f5fd;
}

.rate.bare {
  padding: 2px 2px 0;
  background: none;   /* 보라 박스 + 흰 알약은 어느 서비스에나 있는 모양이라 걷어냈다 */
  gap: 2px;
}

.rate.bare .rate-q {
  margin-right: 8px;
  color: #636e72;
  font-weight: 600;
}

/* 원래 쓰던 선 아이콘에 글자만 붙였다 (예전 .act 와 같은 결) */
.rate-ico {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #636e72;
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
}

.rate-ico svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.rate-ico:hover {
  background: #f1eefc;
  color: #4a3f8f;
}

.rate-q {
  margin-right: 2px;
  color: #4a3f8f;
  font-size: 12.5px;
  font-weight: 700;
}

.rate-btn {
  padding: 6px 11px;
  border: 1px solid #e0dcf4;
  border-radius: 999px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 12.5px;
  cursor: pointer;
}

.rate-btn:hover:not(:disabled) {
  background: #ece9fb;
}

.rate-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.rate-input {
  flex: 1;
  min-width: 160px;
  padding: 6px 10px;
  border: 1px solid #e0dcf4;
  border-radius: 999px;
  background: #ffffff;
  color: #1a1a2e;
  font-size: 12.5px;
  font-family: inherit;
}

.rate-input:focus {
  outline: 2px solid #cfc9f3;
  outline-offset: 1px;
}

.hint {
  margin: 8px 2px 0;
  color: #8a8f98;
  font-size: 12px;
  line-height: 1.55;
}

.hint b {
  color: #6c5ce7;
  font-weight: 600;
}

.save-ask {
  background: #f1eefc;   /* 저장 제안은 평가와 구분되게 조금 더 진한 연보라 */
}

.save-head {
  display: grid;
  gap: 2px;
  margin-right: 4px;
}

.save-sub {
  color: #6b647f;
  font-size: 11.5px;
}

.rate-btn.primary {
  border-color: transparent;
  background: #6c5ce7;
  color: #ffffff;
  font-weight: 600;
}

.rate-btn.primary:hover:not(:disabled) {
  background: #5b4bd6;
}

.rate-btn.folder {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.rate-thanks {
  color: #636e72;
  font-size: 12.5px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: -6px;
}

.act {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #636e72;
  cursor: pointer;
}

.act svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.act:hover {
  background: rgba(74, 63, 143, 0.1);
  color: #4a3f8f;
}

.act.on {
  color: #4a3f8f;
}

.act.on svg {
  fill: currentColor;
}

.cp-foot {
  padding: 0 14px 14px;
}

.cp-quick {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 0 14px 8px;
}

.cp-quick-btn {
  padding: 5px 11px;
  border: 1px solid #e8e6f0;
  border-radius: 999px;
  background: #fff;
  color: #4a3f8f;
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  white-space: nowrap;
}

.cp-quick-btn:hover {
  background: #f4f2fb;
  border-color: #c9c3ea;
}

.cp-usage {
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #4a3f8f;
  padding: 8px 0;
}

.cp-input {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px 14px;
  border-radius: 26px;
  background: #ffffff;
  box-shadow: 0 2px 10px rgba(74, 63, 143, 0.12);
}

.cp-context {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.cp-logo {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  object-fit: cover;
}

.cp-input textarea {
  border: 0;
  outline: 0;
  resize: none;
  font: inherit;
  font-size: 15px;
  line-height: 1.5;
  min-height: 66px;
  padding: 4px 48px 4px 0;
  background: transparent;
  color: #1a1a2e;
}

.cp-send {
  display: grid;
  place-items: center;
  position: absolute;
  right: 14px;
  bottom: 14px;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: #4a3f8f;
  color: #ffffff;
  cursor: pointer;
}

.cp-send:disabled {
  background: #b2a9e3;
  cursor: default;
}

/* 사용자에게 되묻는 카드 - 입력창 위 */
.cp-ask {
  margin-bottom: 10px;
  padding: 14px 14px 8px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #cfc9f3;
  box-shadow: 0 4px 14px rgba(74, 63, 143, 0.12);
  animation: fade-up 0.25s ease-out both;
}

.ask-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 6px 8px;
}

.ask-q {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
}

.ask-skip {
  flex: none;
  border: 0;
  background: transparent;
  color: #636e72;
  font-size: 12px;
  cursor: pointer;
}

.ask-skip:hover {
  color: #4a3f8f;
}

.ask-opt {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 11px 8px;
  border: 0;
  border-top: 1px solid #f0edfa;
  background: transparent;
  color: #1a1a2e;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.ask-opt:hover {
  background: #f4f2fc;
}

.ask-folder {
  margin: 0 0 0 0;
}

.swatches {
  display: flex;
  gap: 8px;
}

.swatch {
  width: 26px;
  height: 26px;
  border: 2px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}

.swatch.on {
  border-color: #4a3f8f;
}

.ask-n {
  display: grid;
  place-items: center;
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #ece9fb;
  color: #4a3f8f;
  font-size: 12px;
  font-weight: 700;
}

/* 진행 문구 - 글자 위로 빛이 지나가고, 단계가 바뀔 때 부드럽게 교체 */
.think {
  font-size: 17px;
  background: linear-gradient(90deg, #8b7ee8 0%, #4a3f8f 35%, #cfc9f3 50%, #4a3f8f 65%, #8b7ee8 100%);
  background-size: 250% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: shimmer 3s linear infinite;
}

.lo-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 52px;
  height: 42px;
}

/* 의도 분석: 퍼져 나가는 레이더 */
.lo-radar {
  position: relative;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #6c5ce7;
}

.lo-radar i {
  position: absolute;
  inset: -1px;
  border: 2px solid #8b7ee8;
  border-radius: 50%;
  animation: radar 2.2s ease-out infinite;
}

.lo-radar i:nth-child(2) { animation-delay: 1.1s; }

@keyframes radar {
  from { transform: scale(1); opacity: 0.8; }
  to { transform: scale(3.2); opacity: 0; }
}

/* 답변 정리: 글줄이 차례로 채워짐 */
.lo-lines {
  display: grid;
  gap: 5px;
  width: 30px;
}

.lo-lines i {
  height: 4px;
  border-radius: 99px;
  background: #b2a9e3;
  transform-origin: left;
  animation: write 2s ease-in-out infinite;
}

.lo-lines i:nth-child(2) { width: 80%; animation-delay: 0.3s; }
.lo-lines i:nth-child(3) { width: 55%; animation-delay: 0.6s; }

@keyframes write {
  0% { transform: scaleX(0); }
  50%, 100% { transform: scaleX(1); }
}

.dots {
  display: inline-flex;
  gap: 4px;
}

.dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6c5ce7;
  animation: bounce 1.4s ease-in-out infinite;
}

.dots i:nth-child(2) { animation-delay: 0.18s; }
.dots i:nth-child(3) { animation-delay: 0.36s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.45; }
  30% { transform: translateY(-5px); opacity: 1; }
}

.secs {
  font-size: 14px;
  color: #9aa3a7;
  font-variant-numeric: tabular-nums;
}

.swap {
  display: inline-block;
  animation: swap-in 0.35s ease-out both;
}

@keyframes swap-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}

@keyframes shimmer {
  from { background-position: 250% 0; }
  to { background-position: -150% 0; }
}

/* 왼쪽 가장자리 폭 조절 손잡이 + 접기(») 버튼 */
.cp-resize {
  position: absolute;
  left: -5px;
  top: 0;
  bottom: 0;
  width: 10px;
  z-index: 3;
  cursor: col-resize;
  touch-action: none;
}

.cp-resize::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: transparent;
  transition: background 0.15s;
}

.cp-resize:hover::after,
.cp-resize.dragging::after {
  background: #8b7ee8;
}

.cp-fold {
  position: absolute;
  left: -15px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 4;
  width: 30px;
  height: 56px;
  border: 1px solid rgba(74, 63, 143, 0.15);
  border-radius: 15px;
  background: #ffffff;
  color: #4a3f8f;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(74, 63, 143, 0.18);
}

.cp-fold:hover {
  background: #f1eefc;
}

/* 예전엔 scale(0.2)에서 최대 480px를 날아와 만화처럼 튀었다.
   살짝 떠오르는 정도로 줄이고, 피드가 쓰는 감속 커브(0.22,1,0.36,1)에 맞춘다. */
@keyframes card-out {
  from { opacity: 0; transform: translateY(18px); }
  to   { opacity: 1; transform: none; }
}

@keyframes filed {
  0% { transform: scale(1); }
  40% { transform: scale(0.965); }
  100% { transform: scale(1); }
}

@keyframes draw {
  to { stroke-dashoffset: 0; }
}

@keyframes fade-up {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}

@keyframes pop {
  from { opacity: 0; transform: scale(0.92); }
  to { opacity: 1; transform: none; }
}

@keyframes twinkle {
  0%, 100% { transform: scale(1) rotate(0); opacity: 1; }
  50% { transform: scale(1.12) rotate(8deg); opacity: 0.85; }
}

/* 모바일은 피드를 밀지 않고 화면 전체를 덮음 */
@media (max-width: 900px) {
  .cp-resize, .cp-fold { display: none; }

  .chat-panel {
    width: 100%;
    border-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-panel { transition: none; }
  .sc-check path { stroke-dashoffset: 0; }
  .lo-radar i, .lo-lines i, .dots i, .think, .hub-orbit, .ai-block, .source-card, .sc-save, .sc-check path, .saved-note, .bubble.user { animation: none; }
}
</style>
