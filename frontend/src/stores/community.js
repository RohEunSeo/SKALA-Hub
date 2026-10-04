// 커뮤니티 목업 상태 (공유 드라이브 / 게시판) - 서버 연동 전이라 mock 데이터 + 내가 바꾼 것만 localStorage에 저장
// 연동 시 이 스토어의 함수 시그니처는 두고 내용만 API 호출로 교체한다.
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { MOCK_BOARD, MOCK_COMMENTS, MOCK_REPOS, MOCK_USERS } from '../mock/community'
import { useFoldersStore } from './folders'
import { useAuthStore } from './auth'

const KEY = 'skala-community-v2'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) ?? {}
  } catch {
    return {}
  }
}

// 글 → 레포 파일 항목
const toDoc = (i, by, msg = '글 추가') => ({
  type: 'doc', title: i.title, msg, ago: '방금 전', kind: 'post', cat: i.category ?? '기타', by, postId: i.postId ?? null,
  summary: '레포에 추가된 글입니다.', url: '', domain: '',
})

export const useCommunityStore = defineStore('community', () => {
  const s = load()
  const starred = ref(s.starred ?? [])
  const watching = ref(s.watching ?? [])
  const cloned = ref(s.cloned ?? {}) // { repoId: folderId }
  const prState = ref(s.prState ?? {}) // { 'repoId#n': 'merged' | 'closed' }
  const issueState = ref(s.issueState ?? {}) // { 'repoId#n': 'open' | 'closed' }
  const readmes = ref(s.readmes ?? {})
  const myRepos = ref(s.myRepos ?? []) // 내가 push한 레포
  const settings = ref(s.settings ?? {}) // { repoId: { mode, visibility } }
  const deleted = ref(s.deleted ?? []) // 삭제한 레포 id
  const added = ref(s.added ?? {}) // { repoId: [doc...] } 레포에 추가/머지된 글
  const myIssues = ref(s.myIssues ?? []) // { repoId, n, title, body, by, ... }
  const myPrs = ref(s.myPrs ?? [])
  const comments = ref(s.comments ?? {}) // 내가 단 댓글 { key: [...] }
  const myBoard = ref(s.myBoard ?? [])
  const likes = ref(s.likes ?? [])
  const solved = ref(s.solved ?? {}) // { boardId: true }

  const all = { starred, watching, cloned, prState, issueState, readmes, myRepos, settings, deleted, added, myIssues, myPrs, comments, myBoard, likes, solved }
  watch(Object.values(all), () => {
    try {
      localStorage.setItem(KEY, JSON.stringify(Object.fromEntries(Object.entries(all).map(([k, v]) => [k, v.value]))))
    } catch { /* 저장 불가 환경은 무시 */ }
  }, { deep: true })

  const myName = () => useAuthStore().user?.name ?? '나'
  const norm = (id) => id.replace(/^@me\//, `${myName()}/`)

  // ===== 레포 =====
  // mock/내 레포에 설정·추가된 글·새 PR/이슈를 합쳐서 화면용 레포로 만든다
  const repos = computed(() =>
    [...myRepos.value, ...MOCK_REPOS.map((r) => (r.owner === '@me' ? { ...r, owner: myName(), id: norm(r.id) } : r))]
      .filter((r) => !deleted.value.includes(r.id))
      .map((r) => {
        const st = settings.value[r.id] ?? {}
        const extra = added.value[r.id] ?? []
        const mine = r.owner === myName()
        const contributors = [...new Set([...r.contributors, ...extra.map((d) => d.by)])]
        return {
          ...r, mine,
          mode: st.mode ?? r.mode, visibility: st.visibility ?? r.visibility,
          files: [...extra, ...r.files], contributors, commits: r.commits + extra.length,
          prs: [...myPrs.value.filter((p) => p.repoId === r.id), ...r.prs],
          issues: [...myIssues.value.filter((i) => i.repoId === r.id), ...r.issues],
        }
      }),
  )
  const getRepo = (owner, name) => repos.value.find((r) => r.owner === owner && r.name === name) ?? null
  const repoById = (id) => repos.value.find((r) => r.id === norm(id)) ?? null
  const myRepoList = computed(() => repos.value.filter((r) => r.mine))
  const userOf = (id) =>
    id === 'me' ? { id, name: myName(), cohort: '', color: '#6c5ce7' } : MOCK_USERS[id] ?? { id, name: id, cohort: '', color: '#b7a6e6' }

  const toggle = (arr, id) => (arr.value = arr.value.includes(id) ? arr.value.filter((x) => x !== id) : [...arr.value, id])
  const isStarred = (id) => starred.value.includes(id)
  const isWatching = (id) => watching.value.includes(id)
  const starCount = (r) => r.stars + (isStarred(r.id) ? 1 : 0)
  const toggleStar = (id) => toggle(starred, id)
  const toggleWatch = (id) => toggle(watching, id)
  const readmeOf = (repo) => readmes.value[repo.id] ?? repo.readme
  const setReadme = (repo, text) => (readmes.value[repo.id] = text)
  const setSetting = (repo, patch) => (settings.value[repo.id] = { ...settings.value[repo.id], ...patch })

  function deleteRepo(repo) {
    myRepos.value = myRepos.value.filter((r) => r.id !== repo.id)
    deleted.value.push(repo.id)
  }

  // push = 내 폴더를 공유 레포로 올리기 (이미 올린 폴더면 갱신)
  // items: [{ postId, title, category }]
  function push({ name, color, items, desc, visibility = 'Public', mode = 'pr' }) {
    const owner = myName()
    const id = `${owner}/${name}`
    const cats = {}
    items.forEach((i) => (cats[i.category] = (cats[i.category] ?? 0) + 1))
    const repo = {
      id, owner, name, official: false, color, desc: desc || `'${name}' 폴더에서 push한 글 ${items.length}개 모음`,
      topics: Object.keys(cats).slice(0, 3), visibility, stars: 0, watchers: 0, forks: 0, commits: 1, updated: '방금 전',
      contributors: ['me'], mode,
      cats: Object.entries(cats).map(([c, n]) => [c, Math.round((n / items.length) * 100)]),
      files: items.map((i) => ({ ...toDoc(i, 'me'), summary: '내 폴더에서 push한 글입니다.' })),
      readme: `# ${name}\n\n내가 모은 글을 **공유**합니다.\n\n- 도움이 되면 ⭐ Star 눌러주세요\n- 추가하고 싶은 글은 **Pull request**로 제안해 주세요`,
      prs: [], issues: [],
    }
    const idx = myRepos.value.findIndex((r) => r.id === id)
    if (idx >= 0) {
      const old = myRepos.value[idx]
      myRepos.value[idx] = { ...repo, desc: desc || old.desc, stars: old.stars, commits: old.commits + 1, readme: old.readme }
    } else myRepos.value.unshift(repo)
    return repo
  }

  // ===== 클론 =====
  // 내 폴더 생성 + 레포의 실제 글(postId) 중 아직 다른 폴더에 없는 것을 담기, 출처(origin) 표시
  // 반환: 'ok' | 'limit'
  function clone(repo) {
    const folders = useFoldersStore()
    const folder = folders.create(repo.name, repo.color)
    if (!folder) return 'limit'
    folder.origin = repo.id
    const ids = repo.files.flatMap((f) => (f.type === 'dir' ? f.items : [f])).map((f) => f.postId).filter((id) => Number.isFinite(id))
    folders.assign(ids.filter((id) => !folders.map[id]), folder.id)
    cloned.value[repo.id] = folder.id
    return 'ok'
  }

  // ===== 글 추가 / PR / 이슈 =====
  const nextN = (repo) => Math.max(0, ...repo.prs.map((p) => p.n), ...repo.issues.map((i) => i.n)) + 1
  const prStateOf = (repo, pr) => prState.value[`${repo.id}#${pr.n}`] ?? pr.state
  const issueStateOf = (repo, i) => issueState.value[`${repo.id}#${i.n}`] ?? i.state

  // 레포에 바로 글 추가 (내 레포이거나 '바로 반영' 레포)
  function addFiles(repo, items, by = 'me') {
    added.value[repo.id] = [...items.map((i) => toDoc(i, by)), ...(added.value[repo.id] ?? [])]
  }
  // PR 만들기 (제안)
  function createPr(repo, { title, items }) {
    const pr = { repoId: repo.id, n: nextN(repo), title, by: 'me', state: 'open', ago: '방금 전', posts: items.map((i) => i.title), items }
    myPrs.value.unshift(pr)
    return pr
  }
  // PR 처리 - merge면 제안한 글이 레포에 반영됨
  function resolvePr(repo, pr, state) {
    prState.value[`${repo.id}#${pr.n}`] = state
    if (state === 'merged') {
      const items = pr.items ?? pr.posts.map((t) => ({ title: t, category: '기타' }))
      if (pr.state !== 'merged') addFiles(repo, items, pr.by)
    }
  }
  function createIssue(repo, { title, body }) {
    const issue = { repoId: repo.id, n: nextN(repo), title, body, by: 'me', state: 'open', ago: '방금 전', comments: 0 }
    myIssues.value.unshift(issue)
    return issue
  }
  const setIssue = (repo, i, state) => (issueState.value[`${repo.id}#${i.n}`] = state)

  // ===== 댓글 (게시판 / 이슈 / PR 공용, key로 구분) =====
  const commentsOf = (key, seed = []) => [...seed, ...(comments.value[key] ?? [])]
  function addComment(key, { text, attach = null, parent = null }) {
    comments.value[key] = [...(comments.value[key] ?? []), { id: `c${Date.now()}`, by: 'me', ago: '방금 전', text, attach, parent }]
  }

  // ===== 게시판 =====
  const board = computed(() => [...myBoard.value, ...MOCK_BOARD].map((p) => ({ ...p, solved: solved.value[p.id] ?? p.solved })))
  const boardPost = (id) => board.value.find((p) => p.id === id) ?? null
  const boardComments = (id) => commentsOf(`board:${id}`, MOCK_COMMENTS[id] ?? [])
  const boardCommentCount = (id) => boardComments(id).length
  const isLiked = (id) => likes.value.includes(id)
  const likeCount = (p) => p.likes + (isLiked(p.id) ? 1 : 0)
  const toggleLike = (id) => toggle(likes, id)
  function createBoard({ type, title, body, attach }) {
    const post = { id: `u${Date.now()}`, type, title, body, attach, by: 'me', ago: '방금 전', likes: 0, solved: false }
    myBoard.value.unshift(post)
    return post
  }
  // 답변 채택 - 정보요청/Q&A를 해결됨으로 (댓글 하나를 채택 표시)
  const adoptedOf = (boardId) => solved.value[boardId]
  function adopt(boardId, commentId) {
    solved.value[boardId] = commentId
  }

  // 글로벌 모달 상태 (Push) - AppLayout에서 한 번만 마운트, 어디서든 열기
  const pushFolderId = ref(undefined) // undefined=닫힘, null=폴더 선택부터, id=그 폴더로 시작
  const openPush = (folderId = null) => (pushFolderId.value = folderId)

  return {
    repos, myRepoList, getRepo, repoById, userOf, starred, cloned, isStarred, isWatching, starCount, toggleStar, toggleWatch,
    readmeOf, setReadme, setSetting, deleteRepo, push, clone,
    prStateOf, issueStateOf, addFiles, createPr, resolvePr, createIssue, setIssue,
    commentsOf, addComment,
    board, boardPost, boardComments, boardCommentCount, isLiked, likeCount, toggleLike, createBoard, adopt, adoptedOf,
    pushFolderId, openPush,
  }
})
