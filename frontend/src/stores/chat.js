// AI 챗봇 패널 상태 - 열림 여부, 대화 메시지, 진행 중 상태, 오늘 남은 횟수
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchGreeting, streamChat } from '../api/chat'
import { fetchPosts } from '../api/posts'
import { useAuthStore } from './auth'
import { useFoldersStore, FOLDER_COLORS } from './folders'
import { useBookmarksStore } from './bookmarks'

const HISTORY_KEY = 'skala-chat-history'
const HISTORY_MAX = 20 // 이전 대화 보관 개수 (목업: 브라우저 localStorage, 서버 연동 시 서버 저장으로 교체)

const WIDTH_KEY = 'skala-chat-width-v3'
const DEFAULT_W = 420 // 처음 열릴 때 폭
const MIN_W = 420 // 패널 최소 폭 (창이 아무리 작아져도 이보다 좁아지지 않음)
const MAX_W = 640 // 패널 최대 폭
const MIN_LEFT = 480 // 여유가 있을 때 왼쪽(사이드바+게시글)에 남겨둘 폭 - 창이 좁으면 MIN_W가 우선

const FIRST_STEP = '질문 의도 분석 중'
const DAILY_LIMIT = 10 // 목업 기본값 - 실제 연동 시 서버 usage 이벤트의 남은 횟수를 사용

export const useChatStore = defineStore('chat', () => {
  const isOpen = ref(false)
  const messages = ref([]) // { id, role: 'user'|'ai', text, sources?, proposal?, feedback?, error? }
  const status = ref(null) // 진행 중 도구 상태 { tool, text } - null이면 대기 중 아님
  const remaining = ref(DAILY_LIMIT)
  const elapsed = ref(0)
  const greeting = ref(null)
  // AI 추천 탭 - 챗봇이 추천한 게시글(피드의 PostCard로 그대로 렌더링), pickSeq가 늘면 피드가 그 탭으로 전환
  const aiPicks = ref([])
  const pickSeq = ref(0)
  const pending = ref(null) // 사용자에게 던진 질문 { question, options: [{label,value}], onPick } - 입력창 위 카드로 표시
  const savedCount = ref(0) // 챗봇으로 저장한 글 수 (런처 폴더 배지용, 목업)
  const history = ref(load()) // 종료된 이전 대화 { id, title, messages }[] 최신순
  // 패널 폭 - 드래그로 조절, 브라우저 창이 작아지면 왼쪽 여유(MIN_LEFT)를 지키도록 자동으로 줄어듦 (경고 없이 보정)
  const stored = ref(Number(localStorage.getItem(WIDTH_KEY)) || DEFAULT_W)
  const winW = ref(window.innerWidth)
  const dragging = ref(false)
  window.addEventListener('resize', () => (winW.value = window.innerWidth))
  const width = computed(() => Math.max(MIN_W, Math.min(stored.value, MAX_W, winW.value - MIN_LEFT)))

  function setWidth(px) {
    stored.value = Math.max(MIN_W, Math.min(Math.round(px), MAX_W, winW.value - MIN_LEFT))
    try { localStorage.setItem(WIDTH_KEY, String(stored.value)) } catch { /* 무시 */ }
  }

  let seq = Date.now() // 복원한 옛 메시지 id와 겹치지 않게 시간값에서 시작

  function load() {
    try { return JSON.parse(localStorage.getItem(HISTORY_KEY)) || [] } catch { return [] }
  }

  function saveHistory() {
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value)) } catch { /* 저장 불가 환경은 무시 */ }
  }

  // 지금 대화를 이전 대화 목록으로 옮기고 화면을 비움 ("대화 종료하기")
  function endConversation() {
    if (status.value) return // 답변 생성 중에는 종료 불가
    if (messages.value.length) {
      const title = messages.value.find((m) => m.role === 'user')?.text ?? '대화'
      history.value = [{ id: ++seq, title, messages: messages.value }, ...history.value].slice(0, HISTORY_MAX)
      saveHistory()
    }
    messages.value = []
    pending.value = null
  }

  // 이전 대화 열기 - 지금 대화는 목록으로 보내고, 고른 대화를 이어서 사용
  function openConversation(id) {
    const target = history.value.find((h) => h.id === id)
    if (!target || status.value) return
    history.value = history.value.filter((h) => h.id !== id)
    endConversation()
    messages.value = target.messages
    saveHistory()
  }

  function open() {
    isOpen.value = true
    if (!greeting.value) fetchGreeting().then((g) => (greeting.value = g))
  }

  function close() {
    isOpen.value = false
  }

  async function ask(question, context) {
    const q = question.trim()
    if (!q || status.value) return
    if (remaining.value <= 0) return askLimit()
    pending.value = null // 새 질문을 직접 입력하면 기존 되묻기는 닫음
    messages.value.push({ id: ++seq, role: 'user', text: q })
    const reply = { id: ++seq, role: 'ai', text: '', sources: null, proposal: null, feedback: null, error: false }
    messages.value.push(reply)
    const current = messages.value[messages.value.length - 1] // 반응형 프록시를 잡아서 갱신
    status.value = { tool: 'intent', text: FIRST_STEP }
    // 생각한 시간(초) - 진행 문구 옆에 표시
    const t0 = Date.now()
    elapsed.value = 0
    const timer = setInterval(() => (elapsed.value = Math.floor((Date.now() - t0) / 1000)), 1000)

    try {
      for await (const ev of streamChat({ question: q, context })) {
        if (ev.type === 'status') status.value = { tool: ev.tool, text: ev.text } // 한 줄의 문구가 단계마다 바뀜
        else if (ev.type === 'token') {
          status.value = null // 글자가 나오기 시작하면 로더는 사라짐
          current.text += ev.text
        } else if (ev.type === 'sources') await applySources(current, ev.posts)
        else if (ev.type === 'keywords') current.keywords = ev.items
        else if (ev.type === 'save_proposal') {
          current.proposal = { folders: ev.folders, postIds: ev.postIds, state: 'pending' }
          askSave(current)
        } else if (ev.type === 'clarify') askUser(ev.question, ev.options, (o) => ask(o.value, context), true)
        else if (ev.type === 'usage') remaining.value -= 1
        else if (ev.type === 'error') {
          current.text = ev.message
          current.error = true
        }
      }
    } finally {
      clearInterval(timer)
      status.value = null
      if (remaining.value <= 0) askLimit() // 방금 마지막 횟수를 썼으면 바로 안내
    }
  }

  // 횟수 소진: 입력창 위 카드에서 대화 종료 → 초기 화면으로
  function askLimit() {
    askUser('오늘 횟수를 모두 사용했어요. 내일 다시 이용할 수 있어요.', [{ label: '대화 종료하기', value: 'end' }], endConversation)
  }

  // 목업(화면 녹화용): 추천 글을 "내가 쓴 글"로만 채움 → 다른 사람 이름이 화면에 나오지 않음.
  // 내 글이 없으면 목업 글로 대체 (연동 시 서버가 준 글 ID로 조회)
  async function applySources(message, mockPosts) {
    const name = useAuthStore().user?.name
    let mine = []
    try {
      if (name) mine = (await fetchPosts({ author: name, page: 0, size: 3 })).data?.content ?? []
    } catch { /* 조회 실패 시 목업 글 사용 */ }
    // 게시글 작성자명은 '4기_판교_5반_노은서'처럼 반/기수가 붙어 있어서 이름 포함 여부로 비교
    mine = mine.filter((p) => p.userName?.includes(name)).slice(0, 3)
    if (!mine.length) {
      message.sources = mockPosts
      return
    }
    aiPicks.value = mine
    pickSeq.value++
    message.sources = mine.map((p) => ({
      id: p.id,
      title: p.aiTitle || (p.content ?? '').split('\n')[0].slice(0, 60),
      category: p.category,
      reactions: p.reactionCount ?? 0,
    }))
  }

  function clearPicks() {
    aiPicks.value = []
  }

  function askUser(question, options, onPick, allowOther = false) {
    pending.value = { question, options, onPick, allowOther } // allowOther: '직접 입력' 선택지 추가
  }

  function dismiss() {
    pending.value = null
  }

  function answer(option) {
    const p = pending.value
    pending.value = null
    p?.onPick(option)
  }

  // 저장 제안: 네/아니요 → 내 폴더 선택(폴더 아이콘) / 새 폴더 만들기 / 폴더 없이 저장
  function askSave(message) {
    askUser('이 글들을 폴더에 저장할까요?', [{ label: '네, 저장할게요', value: 'yes' }, { label: '아니요', value: 'no' }], (o) => {
      if (o.value === 'no') return resolveProposal(message, false)
      askFolder(message)
    })
  }

  function askFolder(message) {
    const foldersStore = useFoldersStore()
    const options = [
      ...foldersStore.folders.map((f) => ({ label: f.name, value: f.id, color: f.color })),
      { label: '새 폴더 만들기', value: '__new__', plus: true },
      { label: '폴더 없이 저장', value: '__none__' },
    ]
    askUser('어느 폴더에 저장할까요?', options, (o) => {
      if (o.value === '__new__') return askNewFolder(message)
      resolveProposal(message, true, o.value === '__none__' ? null : foldersStore.byId(o.value))
    })
  }

  // 새 폴더 이름/색을 입력받는 카드 (입력창 위)
  function askNewFolder(message) {
    pending.value = {
      question: '새 폴더 이름을 정해 주세요',
      form: true,
      defaultColor: FOLDER_COLORS[useFoldersStore().folders.length % FOLDER_COLORS.length],
      onSubmit: ({ name, color }) => resolveProposal(message, true, useFoldersStore().create(name, color)),
    }
  }

  function submitForm(payload) {
    const p = pending.value
    pending.value = null
    p?.onSubmit(payload)
  }

  // 저장 제안 승인/거절 - 실제 저장은 사용자가 버튼을 눌렀을 때만 (AI는 제안만)
  function resolveProposal(message, accepted, folder) {
    if (!message.proposal) return
    message.proposal.folder = folder?.name ?? null // 폴더 없이 저장이면 null
    message.proposal.folderId = folder?.id ?? null
    message.proposal.folderColor = folder?.color ?? null
    if (!accepted) {
      message.proposal.state = 'declined'
      return
    }
    // "저장하는 중" 로더를 잠깐 보여준 뒤 완료 (목업 - 실제 연동 시 저장 API 응답을 기다림)
    message.proposal.state = 'saving'
    setTimeout(() => {
      message.proposal.state = 'saved'
      savedCount.value += message.proposal.postIds.length
      // 추천 글이 실제 게시글(AI 추천 탭에 뜬 글)이면 진짜 저장하기 + 선택한 폴더에 담기 - 사용자가 폴더를 고른 뒤에만 실행
      if (aiPicks.value.length) {
        const ids = aiPicks.value.map((p) => p.id)
        const bookmarks = useBookmarksStore()
        ids.forEach((id) => !bookmarks.bookmarkedPostIds.includes(id) && bookmarks.toggle(id).catch(() => {}))
        useFoldersStore().assign(ids, folder?.id ?? null)
      }
      message.sources?.forEach((p, i) => setTimeout(() => (p.saved = true), i * 160)) // 카드가 차례로 폴더에 꽂히는 느낌
    }, 2700) // 저장 모션이 한 바퀴 이상 보이게
  }

  function setFeedback(message, value, reason = null) {
    message.feedback = { value, reason }
  }

  return { isOpen, aiPicks, pickSeq, clearPicks, elapsed, pending, askUser, answer, dismiss, submitForm, width, dragging, setWidth, messages, status, remaining, greeting, savedCount, history, endConversation, openConversation, open, close, ask, resolveProposal, setFeedback }
})
