// AI 챗봇 패널 상태 - 열림 여부, 대화 메시지, 진행 중 상태, 오늘 남은 횟수
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchAccess, fetchGreeting, sendFeedback, streamChat } from '../api/chat'
import { usePostsStore } from './posts'
import { useFoldersStore, FOLDER_COLORS } from './folders'
import { useBookmarksStore } from './bookmarks'
import { useCommunityStore } from './community'

const HISTORY_KEY = 'skala-chat-history'
const HISTORY_MAX = 20 // 이전 대화 보관 개수 (목업: 브라우저 localStorage, 서버 연동 시 서버 저장으로 교체)

const WIDTH_KEY = 'skala-chat-width-v3'
const DEFAULT_W = 420 // 처음 열릴 때 폭
const MIN_W = 420 // 패널 최소 폭 (창이 아무리 작아져도 이보다 좁아지지 않음)
const MAX_W = 640 // 패널 최대 폭
const MIN_LEFT = 480 // 여유가 있을 때 왼쪽(사이드바+게시글)에 남겨둘 폭 - 창이 좁으면 MIN_W가 우선

const FIRST_STEP = '질문 의도 분석 중'
const DAILY_LIMIT = 10 // 첫 화면용 초기값. 실제 값은 서버가 알려준다 (/api/chat/access, usage 이벤트)

// 공유 드라이브(커뮤니티 레포)는 아직 MVP 범위가 아니다.
// 저장 플로우 자체는 그대로 두고 "push 할까요?" 제안만 끈다 - 만들 때 true로 바꾸면 된다.
const SHOW_PUSH_OFFER = false

// 받은 글자 덩어리를 한 글자씩 흘려보내는 타자기.
// 너무 느리면 답답하고 너무 빠르면 뭉텅이로 보인다. 밀린 양이 많을수록 빨라지게 해서
// 긴 답변도 끝까지 기다리지 않게 한다.
const TYPE_MS = 14

function createTyper(message) {
  const queue = []
  let timer = null

  const tick = () => {
    if (!queue.length) {
      clearInterval(timer)
      timer = null
      return
    }
    // 밀린 글자가 많으면 한 번에 여러 글자 - 긴 답변이 하염없이 길어지지 않게
    const take = Math.max(1, Math.ceil(queue.length / 60))
    message.text += queue.splice(0, take).join('')
  }

  return {
    push(chunk) {
      queue.push(...chunk)
      if (!timer) timer = setInterval(tick, TYPE_MS)
    },
    // 스트림이 끝나도 큐가 남아 있으면 마저 흘려보낸다
    finish() {
      return new Promise((resolve) => {
        const wait = setInterval(() => {
          if (queue.length) return
          clearInterval(wait)
          if (timer) { clearInterval(timer); timer = null }
          resolve()
        }, TYPE_MS)
      })
    },
  }
}

// 추천 카드 등장 타이밍. 템플릿(--delay)과 저장 제안이 같은 값을 봐야 어긋나지 않는다.
// 지속시간은 CSS(.source-card.enter)에 있으니 그 값을 바꾸면 여기도 같이 바꿀 것.
export const CARD_DELAY = 250   // 첫 카드까지
export const CARD_STEP = 260    // 카드 사이 - 한 장씩 올라오는 게 보이는 간격
export const CARD_DURATION = 620  // CSS .source-card.enter 의 0.62s 와 같아야 한다
export const CARD_SETTLE = 650  // 다 뜬 뒤 쉬는 시간 - 바로 물으면 카드를 읽을 틈이 없다
export const cardsDoneMs = (n) =>
  CARD_DELAY + Math.max(0, n - 1) * CARD_STEP + CARD_DURATION + CARD_SETTLE

export const useChatStore = defineStore('chat', () => {
  const isOpen = ref(false)
  const messages = ref([]) // { id, role: 'user'|'ai', text, sources?, proposal?, feedback?, error? }
  const status = ref(null) // 진행 중 도구 상태 { tool, text } - null이면 대기 중 아님
  const remaining = ref(DAILY_LIMIT)
  const limit = ref(DAILY_LIMIT)
  // 챗봇을 열어줄지는 **서버가 판단한다** (판교 5반 + 관리자). null = 아직 안 물어봄.
  // 화면이 같은 규칙을 들고 있으면 서버 .env만 바꿨을 때 어긋나므로 여기선 결과만 보관한다.
  const canUse = ref(null)

  /** 앱이 뜰 때 한 번. 실패하면 allowed:false라 챗봇 버튼이 안 보인다. */
  async function loadAccess() {
    const a = await fetchAccess()
    canUse.value = a.allowed
    limit.value = a.limit || DAILY_LIMIT
    remaining.value = a.remaining ?? DAILY_LIMIT
    return a.allowed
  }
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

  // category: 지금 보고 있는 피드의 카테고리(null이면 전체) - 검색 범위를 그 안으로 제한한다
  // limit: "더 찾아볼까요?"를 수락했을 때만 채워진다 (서버가 offer로 알려준 값)
  // postId: 상세 페이지에서 물을 때만. 있으면 서버가 검색을 건너뛰고 그 글만 다룬다
  async function ask(question, context, category = null, limit = null, postId = null) {
    const q = question.trim()
    if (!q || status.value) return
    if (remaining.value <= 0) return askLimit()
    pending.value = null // 새 질문을 직접 입력하면 기존 되묻기는 닫음
    messages.value.push({ id: ++seq, role: 'user', text: q })
    const reply = { id: ++seq, role: 'ai', text: '', sources: null, proposal: null, feedback: null, error: false, logId: null }
    messages.value.push(reply)
    const current = messages.value[messages.value.length - 1] // 반응형 프록시를 잡아서 갱신
    status.value = { tool: 'intent', text: FIRST_STEP }
    // 생각한 시간(초) - 진행 문구 옆에 표시
    const t0 = Date.now()
    elapsed.value = 0
    const timer = setInterval(() => (elapsed.value = Math.floor((Date.now() - t0) / 1000)), 1000)

    // 서버는 글자를 덩어리로 보낸다(Gemini가 그렇게 준다). 그대로 붙이면 뭉텅이로 나타난다.
    // 큐에 쌓아두고 일정 간격으로 한 글자씩 꺼내 붙여 타이핑처럼 보이게 한다.
    const typer = createTyper(current)

    try {
      for await (const ev of streamChat({ question: q, context, category, limit, postId })) {
        if (ev.type === 'status') status.value = { tool: ev.tool, text: ev.text } // 한 줄의 문구가 단계마다 바뀜
        else if (ev.type === 'token') {
          status.value = null // 글자가 나오기 시작하면 로더는 사라짐
          typer.push(ev.text)
        } else if (ev.type === 'sources') {
          // 서버는 토큰을 다 보낸 뒤 sources 를 주지만, 화면은 타자기라 아직 쓰는 중이다.
          // 기다리지 않으면 답변이 끝나기도 전에 카드가 올라와 둘이 겹쳐 보인다.
          await typer.finish()
          await applySources(current, ev.posts, category, q)
        }
        else if (ev.type === 'keywords') current.keywords = ev.items
        else if (ev.type === 'offer') {
          // "추천해 드릴까요?" → 네를 눌러야 그때 검색 모드로 넘어감.
          // scope === 'all' 이면 탭 안에 답이 없어서 전체로 넓히는 경우다.
          const toAll = ev.scope === 'all'
          const more = ev.scope === 'more'
          const yes = toAll ? '네, 전체에서 찾아주세요' : more ? '네, 더 보여주세요' : '네, 추천해 주세요'
          askUser(ev.question, [{ label: yes, value: 'yes' }, { label: '아니요', value: 'no' }], (o) => {
            if (o.value !== 'yes') return
            // 검색만 넓히면 피드는 그대로 학습자료 탭이라, 추천받은 글이 화면에 안 보인다.
            // 탭도 같이 전체로 돌린다 (사이드바 선택 표시도 이 값을 본다).
            if (toAll) usePostsStore().setCategory(null)
            ask(ev.query, toAll ? '전체 피드' : context, toAll ? null : category, ev.limit ?? null)
          })
        }
        else if (ev.type === 'save_proposal') {
          current.proposal = { folders: ev.folders, postIds: ev.postIds, state: 'pending' }
          // 카드가 한 장씩 다 뜬 뒤에 묻는다. 같이 나오면 뭘 보라는 건지 알 수 없다.
          setTimeout(() => askSave(current), cardsDoneMs(ev.postIds?.length ?? 0))
        } else if (ev.type === 'clarify') askUser(ev.question, ev.options, (o) => ask(o.value, context, category), true)
        // 서버가 남은 횟수를 알려준다. 없으면(옛 형식) 화면이 직접 1 뺀다
        else if (ev.type === 'usage') remaining.value = ev.remaining ?? remaining.value - 1
        // 👍👎를 어느 답변에 달지 알려면 서버가 남긴 로그 번호가 필요하다
        else if (ev.type === 'done') current.logId = ev.logId ?? null
        else if (ev.type === 'error') {
          current.text = ev.message
          current.error = true
        }
      }
    } finally {
      await typer.finish()
      clearInterval(timer)
      status.value = null
      if (remaining.value <= 0) askLimit() // 방금 마지막 횟수를 썼으면 바로 안내
    }
  }

  // 횟수 소진: 입력창 위 카드에서 대화 종료 → 초기 화면으로
  function askLimit() {
    askUser('오늘 횟수를 모두 사용했어요. 내일 다시 이용할 수 있어요.', [{ label: '대화 종료하기', value: 'end' }], endConversation)
  }

  // 서버가 고른 글을 그대로 피드에 반영한다.
  // posts는 관련도 순이고, 피드도 그 순서를 지켜 위로 올린다 (stores/posts.js의 setPicks).
  async function applySources(message, posts, category = null, query = '') {
    message.sources = posts.map((p) => ({ ...p }))  // 메시지마다 저장 상태가 따로 놀게 복사
    if (!posts.length) return

    aiPicks.value = posts
    pickSeq.value++
    await usePostsStore().setPicks(posts.map((p) => p.id), query)
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
    askUser('이 글들을 내 폴더에 저장할까요?', [{ label: '네, 저장할게요', value: 'yes' }, { label: '아니요', value: 'no' }], (o) => {
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
      onSubmit: async ({ name, color }) =>
        resolveProposal(message, true, await useFoldersStore().create(name, color)),
    }
  }

  function submitForm(payload) {
    const p = pending.value
    pending.value = null
    p?.onSubmit(payload)
  }

  // 저장 제안 승인/거절 - 실제 저장은 사용자가 버튼을 눌렀을 때만 (AI는 제안만)
  async function resolveProposal(message, accepted, folder) {
    if (!message.proposal) return
    message.proposal.folder = folder?.name ?? null // 폴더 없이 저장이면 null
    message.proposal.folderId = folder?.id ?? null
    message.proposal.folderColor = folder?.color ?? null
    if (!accepted) {
      message.proposal.state = 'declined'
      return
    }
    message.proposal.state = 'saving'

    // 서버가 알려준 글 번호를 쓴다. aiPicks는 피드 표시용이라 경로(비슷한 글·인기 글)에 따라 비어 있다.
    const ids = message.proposal.postIds ?? []
    const bookmarks = useBookmarksStore()
    const motion = new Promise((r) => setTimeout(r, 2700)) // 저장 모션이 한 바퀴 이상 보이게

    try {
      // 북마크를 **먼저 끝낸 뒤** 폴더에 담는다.
      // 서버의 assign은 '북마크 행이 있으면 폴더를 지정'하는 구조라, 동시에 보내면
      // 아직 행이 없어서 조용히 건너뛴다 - 저장은 됐는데 폴더엔 없는 상태가 된다.
      await Promise.all(
        ids
          .filter((id) => !bookmarks.bookmarkedPostIds.includes(id))
          .map((id) => bookmarks.toggle(id).catch(() => {})),
      )
      await useFoldersStore().assign(ids, folder?.id ?? null)
    } catch { /* 개별 실패는 위에서 삼킨다 - 여기까지 오면 화면은 '저장함'으로 마무리 */ }

    await motion
    message.proposal.state = 'saved'
    savedCount.value += ids.length
    message.sources?.forEach((p, i) => setTimeout(() => (p.saved = true), i * 160)) // 카드가 차례로 폴더에 꽂히는 느낌
    // 폴더에 담았으면 공유 레포로 push할지 물어봄 (MVP 범위 밖이라 꺼둠)
    if (SHOW_PUSH_OFFER && folder) setTimeout(() => askPush(message, folder), 900)
  }

  // 공유 드라이브 push 제안 - 사용자가 네를 눌렀을 때만 (목업: 프론트 저장)
  function askPush(message, folder) {
    askUser(`'${folder.name}' 폴더를 공유 드라이브에 push 할까요?`, [{ label: '네, push할게요', value: 'yes' }, { label: '아니요', value: 'no' }], (o) => {
      if (o.value === 'no') return (message.push = { state: 'declined' })
      message.push = { state: 'pushing', folder: folder.name, color: folder.color }
      setTimeout(() => {
        const items = (message.sources ?? []).map((p) => ({ postId: aiPicks.value.length ? p.id : null, title: p.title, category: p.category }))
        const repo = useCommunityStore().push({ name: folder.name, color: folder.color, items })
        message.push = { state: 'pushed', folder: folder.name, color: folder.color, owner: repo.owner, count: items.length }
      }, 2600)
    })
  }

  // 👍👎. 화면은 바로 바꾸고 서버 전송은 기다리지 않는다 - 눌렀는데 반응이 늦으면 더 이상하다.
  // 전송이 실패해도 되돌리지 않는다(피드백이 본론이 아니다). 👎는 이유를 고를 때 한 번 더 덮어쓴다.
  // 좋아요/보통/별로. 서버는 1/0/-1 로 받는다 (chat_logs.feedback)
  const FEEDBACK_SCORE = { up: 1, mid: 0, down: -1 }

  function setFeedback(message, value, reason = null) {
    message.feedback = { value, reason }
    sendFeedback(message.logId, FEEDBACK_SCORE[value] ?? 0, reason)
  }

  return { isOpen, aiPicks, pickSeq, clearPicks, elapsed, pending, askUser, answer, dismiss, submitForm, width, dragging, setWidth, messages, status, remaining, limit, canUse, loadAccess, greeting, savedCount, history, endConversation, openConversation, open, close, ask, resolveProposal, setFeedback }
})
