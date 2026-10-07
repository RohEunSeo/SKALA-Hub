<script setup>
// 커뮤니티 > 레포 상세 - GitHub 레포 화면 레이아웃 (Code / Issues / Pull requests 탭, README, 사이드바)
// 소유자 전용(README 편집, PR Merge/Close, Settings)은 repo.mine일 때만 노출. 목업 데이터는 localStorage에 저장
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import AuthRequired from '../components/AuthRequired.vue'
import FolderIcon from '../components/FolderIcon.vue'
import UserDot from '../components/community/UserDot.vue'
import PostModal from '../components/community/PostModal.vue'
import AddPostsModal from '../components/community/AddPostsModal.vue'
import CommentThread from '../components/community/CommentThread.vue'
import SaveLoader from '../components/chat/SaveLoader.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunityStore } from '../stores/community'
import { useToastStore } from '../stores/toast'
import { miniMarkdown } from '../utils/miniMarkdown'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const store = useCommunityStore()
const toast = useToastStore()

const repo = computed(() => store.getRepo(route.params.owner, route.params.repo))
const tab = ref('code')
const isMine = computed(() => repo.value.mine)
const isCloned = computed(() => !!store.cloned[repo.value.id])
const isPr = computed(() => repo.value.mode === 'pr')

// 상단 드롭다운 (Code ▾ / Add file ▾) - 한 번에 하나만 열림
const menu = ref('')
const toggleMenu = (name) => (menu.value = menu.value === name ? '' : name)

const cloning = ref(false)
function doClone() {
  menu.value = ''
  if (cloning.value) return
  cloning.value = true
  setTimeout(() => {
    cloning.value = false
    if (store.clone(repo.value) === 'limit') return toast.show('내 폴더가 가득 찼어요 (최대 20개)')
    toast.show(`내 폴더에 '${repo.value.name}' 폴더가 만들어졌어요`, {
      actionLabel: '폴더 보기',
      onAction: () => router.push('/feed?tab=ai'),
    })
  }, 2200) // 문서가 폴더로 들어가는 모션이 한 바퀴 보이게
}
function copyLink() {
  menu.value = ''
  navigator.clipboard?.writeText(location.href)
  toast.show('레포 링크를 복사했어요')
}
const soon = (msg) => { menu.value = ''; toast.show(msg) }

// README 편집 (Write / Preview)
const editing = ref(false)
const draft = ref('')
const previewing = ref(false)
function startEdit() { draft.value = store.readmeOf(repo.value); previewing.value = false; editing.value = true }
function saveReadme() { store.setReadme(repo.value, draft.value); editing.value = false; toast.show('README를 저장했어요') }

// 폴더 안으로 들어가기 (GitHub처럼 breadcrumb) + 항목 상세 펼치기
const cwd = ref(null) // 열어 둔 폴더 이름 (null이면 루트)
const curDir = computed(() => repo.value.files.find((x) => x.type === 'dir' && x.title === cwd.value))
// 폴더가 먼저, 그다음 글 (GitHub 파일 표처럼)
const rows = computed(() => (curDir.value ? curDir.value.items : [...repo.value.files].sort((a, b) => (a.type === 'dir' ? 0 : 1) - (b.type === 'dir' ? 0 : 1))))
const modal = ref(null) // { items, start } - 열려 있는 글 보기 모달
const postsIn = (list) => list.filter((x) => x.type === 'doc' && x.kind === 'post')
function clickRow(fl) {
  if (fl.type === 'dir') { cwd.value = fl.title; return }
  // 링크 모음은 원본을 새 탭으로, 슬랙 글은 레포 안에서 모달로 (같은 폴더의 글끼리 ‹ › 이동)
  if (fl.kind === 'link') return fl.url && window.open(fl.url, '_blank', 'noopener')
  const list = postsIn(rows.value)
  modal.value = { items: list, start: list.indexOf(fl) }
}
const countOf = (fl) => fl.items?.length ?? 0
const KIND = { post: '💬 슬랙 글', link: '🔗 링크 모음' }

const readmeHtml = computed(() => miniMarkdown(store.readmeOf(repo.value)))
const draftHtml = computed(() => miniMarkdown(draft.value))
const openPrs = computed(() => repo.value.prs.filter((p) => store.prStateOf(repo.value, p) === 'open').length)
const openIssues = computed(() => repo.value.issues.filter((i) => store.issueStateOf(repo.value, i) === 'open').length)

// 글 추가(바로) / Pull request 제안 - 내 레포이거나 '바로 반영' 레포면 바로 추가, 아니면 PR
const addModal = ref('') // '' | 'direct' | 'pr'
const canDirect = computed(() => isMine.value || repo.value.mode === 'open')
function openAdd(mode) { menu.value = ''; tab.value = 'code'; addModal.value = mode }
function submitAdd({ title, items }) {
  if (addModal.value === 'direct') {
    store.addFiles(repo.value, items)
    toast.show(`글 ${items.length}개를 레포에 추가했어요`)
  } else {
    store.createPr(repo.value, { title, items })
    tab.value = 'pulls'
    toast.show('Pull request를 만들었어요 · 소유자가 확인하면 반영돼요')
  }
  addModal.value = ''
}

// 이슈/PR 상세 + 새 이슈 작성
const detail = ref(null) // { type: 'issue'|'pr', n }
const issueForm = ref(false)
const issueTitle = ref('')
const issueBody = ref('')
const curIssue = computed(() => detail.value?.type === 'issue' ? repo.value.issues.find((i) => i.n === detail.value.n) : null)
const curPr = computed(() => detail.value?.type === 'pr' ? repo.value.prs.find((p) => p.n === detail.value.n) : null)
function goTab(t) { tab.value = t; detail.value = null; issueForm.value = false }
function submitIssue() {
  if (!issueTitle.value.trim()) return
  const i = store.createIssue(repo.value, { title: issueTitle.value.trim(), body: issueBody.value.trim() })
  issueTitle.value = ''; issueBody.value = ''; issueForm.value = false
  detail.value = { type: 'issue', n: i.n }
}
const commentCount = (key, seed = 0) => store.commentsOf(key).length + seed

// Settings (소유자만)
function removeRepo() {
  if (!window.confirm(`'${repo.value.name}' 레포를 삭제할까요? 되돌릴 수 없어요.`)) return
  store.deleteRepo(repo.value)
  toast.show('레포를 삭제했어요')
  router.push('/community/drive')
}
const catColors = ['#6c5ce7', '#b7a6e6', '#99b8f5', '#d8d0f5']

function merge(pr) { store.resolvePr(repo.value, pr, 'merged'); toast.show(`#${pr.n} 을(를) merge 했어요 · 글이 레포에 반영됐어요`) }
function close(pr) { store.resolvePr(repo.value, pr, 'closed'); toast.show(`#${pr.n} 을(를) 닫았어요`) }
</script>

<template>
  <AppLayout :max-width="1200" @click="menu = ''">
    <AuthRequired v-if="!authStore.isAuthenticated" message="커뮤니티는 로그인이 필요합니다" />
    <div v-else-if="!repo" class="rd-none">
      레포를 찾을 수 없어요 · <RouterLink to="/community/drive">드라이브로 돌아가기</RouterLink>
    </div>
    <div v-else class="rd" @click="menu = ''">
 <AddPostsModal v-if="addModal" :mode="addModal" :repo-name="`${repo.owner} / ${repo.name}`" @close="addModal = ''" @submit="submitAdd" />
      <!-- 클론 중 모션: 문서가 내 폴더로 들어감 -->
      <Teleport to="body">
        <div v-if="cloning" class="clone-ov"><div class="clone-box"><SaveLoader :width="72" :color="repo.color" /><p>내 폴더로 clone하는 중…</p><code>$ skala clone {{ repo.owner }}/{{ repo.name }}</code></div></div>
      </Teleport>
      <PostModal v-if="modal" :items="modal.items" :start="modal.start" :path="cwd ? `${repo.name} / ${cwd}` : repo.name" @close="modal = null" />
      <RouterLink to="/community/drive" class="rd-back">‹ 공유 드라이브</RouterLink>

      <!-- 헤더: 레포명 + 공개범위 / Watch · Fork · Star -->
      <div class="rd-head">
        <div class="rd-title">
          <FolderIcon :color="repo.color" :size="22" />
          <span class="rd-name">{{ repo.name }}</span>
          <span class="rd-pill">{{ repo.visibility }}</span>
          <span v-if="repo.official" class="rd-official">공식</span>
        </div>
        <div class="rd-actions">
          <div class="btn-group">
            <button class="gb" @click="store.toggleWatch(repo.id)">👁 {{ store.isWatching(repo.id) ? 'Watching' : 'Watch' }} <b>{{ repo.watchers + (store.isWatching(repo.id) ? 1 : 0) }}</b></button>
          </div>
          <div class="btn-group">
            <button class="gb" @click="soon('Fork는 clone으로 대신해요 · Code ▾ 에서 Clone을 눌러보세요')">⑂ Fork <b>{{ repo.forks }}</b></button>
          </div>
          <div class="btn-group">
            <button class="gb" :class="{ starred: store.isStarred(repo.id) }" @click="store.toggleStar(repo.id)">
              {{ store.isStarred(repo.id) ? '★ Starred' : '☆ Star' }} <b>{{ store.starCount(repo) }}</b>
            </button>
          </div>
        </div>
      </div>

      <!-- 탭 바 -->
      <nav class="rd-tabs">
        <button class="rd-tab" :class="{ on: tab === 'code' }" @click="goTab('code')">&lt;&gt; Code</button>
        <button class="rd-tab" :class="{ on: tab === 'issues' }" @click="goTab('issues')">⊙ Issues <i v-if="openIssues">{{ openIssues }}</i></button>
        <button class="rd-tab" :class="{ on: tab === 'pulls' }" @click="goTab('pulls')">⇄ Pull requests <i v-if="openPrs">{{ openPrs }}</i></button>
        <button v-if="isMine" class="rd-tab" :class="{ on: tab === 'settings' }" @click="goTab('settings')">⚙ Settings</button>
      </nav>

      <div class="rd-body">
        <div class="rd-main">
          <!-- ===== Code ===== -->
          <template v-if="tab === 'code'">
            <div class="toolbar">
              <span class="branch">⎇ main ▾</span>
              <span class="tb-info">{{ curDir ? curDir.items.length : repo.files.length }} 항목</span>
              <div class="tb-right">
                <div class="menu-wrap" @click.stop>
                  <button class="tb-btn" @click="toggleMenu('add')">Add file ▾</button>
                  <div v-if="menu === 'add'" class="menu">
                    <button v-if="canDirect" @click="openAdd('direct')">＋ 내 글 바로 추가</button>
                    <button v-else @click="openAdd('pr')">⇄ 글 추가 제안 (PR)</button>
                  </div>
                </div>
                <div class="menu-wrap" @click.stop>
                  <button class="code-btn" :disabled="cloning" @click="toggleMenu('code')">
                    <span v-if="cloning" class="spin"></span><template v-else>&lt;&gt; Code ▾</template>
                  </button>
                  <div v-if="menu === 'code'" class="menu menu-code">
                    <div class="menu-title">Clone</div>
                    <code class="cmd">$ skala clone {{ repo.owner }}/{{ repo.name }}</code>
                    <button @click="doClone">{{ isCloned ? '⑂ 다시 내 폴더로 Clone' : '⑂ 내 폴더로 Clone' }}</button>
                    <button @click="copyLink">🔗 링크 복사</button>
                  </div>
                </div>
              </div>
            </div>

            <div class="files">
              <div class="commit-bar">
                <UserDot :user="store.userOf(repo.contributors[0])" :size="20" />
                <b>{{ store.userOf(repo.contributors[0]).name }}</b>
                <span class="cm-msg">{{ repo.files[0].msg }}</span>
                <span class="cm-right">{{ repo.updated }} · 🕘 {{ repo.commits }} Commits</span>
              </div>
              <div v-if="curDir" class="crumb">
                <button @click="cwd = null">{{ repo.name }}</button> / <b>{{ cwd }}</b>
                <span class="crumb-cnt">저장한 항목 {{ curDir.items.length }}개</span>
              </div>
              <template v-for="fl in rows" :key="fl.title">
                <div class="file-row" @click="clickRow(fl)">
                  <span class="f-icon">
                    <FolderIcon v-if="fl.type === 'dir'" :color="repo.color" :size="16" />
                    <template v-else>{{ fl.kind === 'link' ? '🔗' : '📄' }}</template>
                  </span>
                  <span class="f-name">{{ fl.title }}<span v-if="fl.type === 'dir'" class="f-count">{{ countOf(fl) }}</span></span>
                  <span class="f-msg">{{ fl.type === 'dir' ? fl.msg : (fl.summary || fl.msg) }}</span>
                  <span class="f-ago">{{ fl.ago }}</span>
                </div>
                </template>
            </div>

            <div class="readme">
              <div class="readme-head">
                <span>📖 README</span>
                <button v-if="!editing && isMine" class="icon-btn" title="README 편집" @click="startEdit">✏️</button>
              </div>
              <div v-if="!editing" class="md" v-html="readmeHtml"></div>
              <div v-else class="editor">
                <div class="ed-tabs">
                  <button :class="{ on: !previewing }" @click="previewing = false">Write</button>
                  <button :class="{ on: previewing }" @click="previewing = true">Preview</button>
                </div>
                <textarea v-if="!previewing" v-model="draft" class="ed-area" rows="12"></textarea>
                <div v-else class="md ed-prev" v-html="draftHtml"></div>
                <div class="ed-foot">
                  <span class="ed-hint"># 제목 · **굵게** · - 목록 · > 인용 · [글자](https://…)</span>
                  <button class="gb" @click="editing = false">취소</button>
                  <button class="save-btn" @click="saveReadme">저장</button>
                </div>
              </div>
            </div>
          </template>

          <!-- ===== Issues (토론/정보요청) ===== -->
          <template v-else-if="tab === 'issues'">
            <!-- 이슈 상세 -->
            <template v-if="curIssue">
              <button class="back" @click="detail = null">‹ Issues</button>
              <div class="dt-head">
                <h2>{{ curIssue.title }} <span class="dt-n">#{{ curIssue.n }}</span></h2>
                <span class="st" :class="store.issueStateOf(repo, curIssue)">{{ store.issueStateOf(repo, curIssue) === 'open' ? '● Open' : '✓ Closed' }}</span>
              </div>
              <div class="dt-sub"><UserDot :user="store.userOf(curIssue.by)" :size="18" /> {{ store.userOf(curIssue.by).name }} · {{ curIssue.ago }}</div>
              <p v-if="curIssue.body" class="dt-body">{{ curIssue.body }}</p>
              <CommentThread :comment-key="`issue:${repo.id}#${curIssue.n}`" allow-repo />
              <div v-if="isMine || curIssue.by === 'me'" class="dt-act">
                <button class="gb" @click="store.setIssue(repo, curIssue, store.issueStateOf(repo, curIssue) === 'open' ? 'closed' : 'open')">
                  {{ store.issueStateOf(repo, curIssue) === 'open' ? '✓ Close issue' : '↺ Reopen' }}
                </button>
              </div>
            </template>
            <template v-else>
              <div class="tb-line"><span class="tb-info">토론 · 정보 요청</span><button class="code-btn" @click="issueForm = !issueForm">New issue</button></div>
              <div v-if="issueForm" class="form-card">
                <input v-model="issueTitle" maxlength="80" placeholder="제목 (예: 3과목 자료가 더 있나요?)" />
                <textarea v-model="issueBody" rows="4" placeholder="자세한 내용을 적어주세요"></textarea>
                <div class="form-foot"><button class="gb" @click="issueForm = false">취소</button><button class="save-btn" :disabled="!issueTitle.trim()" @click="submitIssue">이슈 만들기</button></div>
              </div>
              <div class="list">
                <div v-for="i in repo.issues" :key="i.n" class="li clickable" @click="detail = { type: 'issue', n: i.n }">
                  <span class="st" :class="store.issueStateOf(repo, i)">{{ store.issueStateOf(repo, i) === 'open' ? '● Open' : '✓ Closed' }}</span>
                  <div class="li-main"><b>{{ i.title }}</b><span class="li-sub">#{{ i.n }} · {{ store.userOf(i.by).name }} · {{ i.ago }}</span></div>
                  <span class="li-cm">💬 {{ commentCount(`issue:${repo.id}#${i.n}`, i.comments) }}</span>
                </div>
                <p v-if="!repo.issues.length" class="empty">아직 토론이 없어요. 궁금한 점을 남겨보세요!</p>
              </div>
            </template>
          </template>

          <!-- ===== Pull requests ===== -->
          <template v-else-if="tab === 'pulls'">
            <template v-if="curPr">
              <button class="back" @click="detail = null">‹ Pull requests</button>
              <div class="dt-head">
                <h2>{{ curPr.title }} <span class="dt-n">#{{ curPr.n }}</span></h2>
                <span class="st" :class="store.prStateOf(repo, curPr)">{{ { open: '⇄ Open', merged: '✔ Merged', closed: '✕ Closed' }[store.prStateOf(repo, curPr)] }}</span>
              </div>
              <div class="dt-sub"><UserDot :user="store.userOf(curPr.by)" :size="18" /> {{ store.userOf(curPr.by).name }}님이 글 {{ curPr.posts.length }}개를 추가하자고 제안했어요 · {{ curPr.ago }}</div>
              <div class="list pr-files"><div v-for="t in curPr.posts" :key="t" class="li">📄 <span>{{ t }}</span><em class="plus">+ 추가</em></div></div>
              <div v-if="isMine && store.prStateOf(repo, curPr) === 'open'" class="dt-act">
                <button class="merge" @click="merge(curPr)">Merge pull request</button>
                <button class="gb" @click="close(curPr)">Close</button>
              </div>
              <CommentThread :comment-key="`pr:${repo.id}#${curPr.n}`" />
            </template>
            <template v-else>
              <div class="tb-line">
                <span class="tb-info">기여 방식: <b>{{ isPr ? '승인 필요 (PR)' : '바로 반영 (오픈)' }}</b></span>
                <button class="code-btn" @click="canDirect ? openAdd('direct') : openAdd('pr')">{{ canDirect ? '글 바로 추가' : 'New pull request' }}</button>
              </div>
              <div class="list">
                <div v-for="p in repo.prs" :key="p.n" class="li pr clickable" @click="detail = { type: 'pr', n: p.n }">
                  <span class="st" :class="store.prStateOf(repo, p)">
                    {{ { open: '⇄ Open', merged: '✔ Merged', closed: '✕ Closed' }[store.prStateOf(repo, p)] }}
                  </span>
                  <div class="li-main">
                    <b>{{ p.title }}</b>
                    <span class="li-sub">#{{ p.n }} · {{ store.userOf(p.by).name }} · {{ p.ago }} · 글 {{ p.posts.length }}개</span>
                  </div>
                  <div v-if="isMine && store.prStateOf(repo, p) === 'open'" class="pr-act" @click.stop>
                    <button class="merge" @click="merge(p)">Merge</button>
                    <button class="gb" @click="close(p)">Close</button>
                  </div>
                </div>
                <p v-if="!repo.prs.length" class="empty">{{ isPr ? '열린 제안이 없어요' : '이 레포는 승인 없이 바로 반영돼요' }}</p>
              </div>
            </template>
          </template>

          <!-- ===== Settings (소유자만) ===== -->
          <template v-else>
            <div class="set-sec">
              <h3>기여 방식</h3>
              <label class="radio"><input type="radio" value="pr" :checked="repo.mode === 'pr'" @change="store.setSetting(repo, { mode: 'pr' })" /> <b>승인 필요 (PR)</b> — 다른 사람은 글 추가를 제안하고, 내가 Merge해야 반영돼요</label>
              <label class="radio"><input type="radio" value="open" :checked="repo.mode === 'open'" @change="store.setSetting(repo, { mode: 'open' })" /> <b>바로 반영</b> — 누구나 승인 없이 글을 바로 추가할 수 있어요</label>
            </div>
            <div class="set-sec">
              <h3>공개 범위</h3>
              <select :value="repo.visibility" class="set-select" @change="store.setSetting(repo, { visibility: $event.target.value })">
                <option>Public</option><option>링크만</option><option>Private</option>
              </select>
              <p class="set-hint">Public은 공유 드라이브 목록에 보이고, 링크만/Private은 목록에서 숨겨져요 (목업에서는 표시만 바뀌어요).</p>
            </div>
            <div class="set-sec danger">
              <h3>위험 구역</h3>
              <p class="set-hint">레포를 삭제하면 안의 글 목록과 PR/이슈가 모두 사라져요. (원본 슬랙 글은 삭제되지 않아요)</p>
              <button class="del-btn" @click="removeRepo">이 레포 삭제</button>
            </div>
          </template>
        </div>

        <!-- 사이드바 -->
        <aside class="rd-side">
          <div class="side-sec">
            <h3>About</h3>
            <p class="about">{{ repo.desc }}</p>
            <div class="topics"><span v-for="t in repo.topics" :key="t" class="topic">{{ t }}</span></div>
            <ul class="stats">
              <li>📖 Readme</li>
              <li>☆ {{ store.starCount(repo) }} stars</li>
              <li>👁 {{ repo.watchers + (store.isWatching(repo.id) ? 1 : 0) }} watching</li>
              <li>⑂ {{ repo.forks }} clones</li>
            </ul>
          </div>
          <div class="side-sec">
            <h3>Contributors <span class="cnt">{{ repo.contributors.length }}</span></h3>
            <div v-for="c in repo.contributors" :key="c" class="contrib">
              <UserDot :user="store.userOf(c)" :size="22" /> <b>{{ store.userOf(c).name }}</b> <span class="coh">{{ store.userOf(c).cohort }}</span>
            </div>
          </div>
          <div class="side-sec">
            <h3>카테고리 구성</h3>
            <div class="bar"><i v-for="(c, idx) in repo.cats" :key="c[0]" :style="{ width: `${c[1]}%`, background: catColors[idx % 4] }"></i></div>
            <div class="legend"><span v-for="(c, idx) in repo.cats" :key="c[0]"><i :style="{ background: catColors[idx % 4] }"></i>{{ c[0] }} {{ c[1] }}%</span></div>
          </div>
        </aside>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.rd-none { padding: 80px 0; text-align: center; color: #636e72; }
.rd-back { font-size: 13px; color: #636e72; text-decoration: none; }
.rd-back:hover { color: #4a3f8f; }
.rd-head { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin: 12px 0 14px; }
.rd-title { display: flex; align-items: center; gap: 8px; }
.rd-name { font-size: 20px; font-weight: 600; color: #1a1a2e; }
.rd-pill { padding: 0 8px; border: 1px solid #d8d8e4; border-radius: 10px; font-size: 12px; color: #636e72; }
.rd-official { padding: 1px 8px; border-radius: 10px; background: #4a3f8f; color: #fff; font-size: 11px; font-weight: 600; }
.rd-actions { display: flex; gap: 8px; }
.gb { padding: 5px 12px; border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; font-size: 13px; font-weight: 500; color: #1a1a2e; cursor: pointer; }
.gb:hover { background: #eef1f4; }
.gb b { margin-left: 4px; padding: 0 6px; border-radius: 10px; background: #e3e6ea; font-size: 12px; }
.gb.starred { color: #b5830b; }
.rd-tabs { display: flex; gap: 4px; border-bottom: 1px solid #d0d7de; margin-bottom: 16px; overflow-x: auto; }
.rd-tab { padding: 8px 12px; border: none; border-bottom: 2px solid transparent; background: none; font-size: 14px; color: #636e72; cursor: pointer; white-space: nowrap; }
.rd-tab.on { border-bottom-color: #6c5ce7; color: #1a1a2e; font-weight: 600; }
.rd-tab i { margin-left: 4px; padding: 0 6px; border-radius: 10px; background: #e3e6ea; font-size: 12px; font-style: normal; }
.rd-body { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 24px; align-items: start; }
.toolbar, .tb-line { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.tb-line { justify-content: space-between; }
.branch { padding: 5px 12px; border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; font-size: 13px; font-weight: 500; }
.tb-info { font-size: 13px; color: #636e72; }
.tb-right { display: flex; gap: 8px; margin-left: auto; }
.menu-wrap { position: relative; }
.tb-btn { padding: 5px 12px; border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; font-size: 13px; font-weight: 500; cursor: pointer; }
.code-btn { display: inline-flex; align-items: center; justify-content: center; min-width: 78px; padding: 5px 14px; border: 1px solid #1a7f37; border-radius: 6px; background: #1f883d; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.code-btn:hover { background: #1a7f37; }
.spin { width: 13px; height: 13px; border: 2px solid rgba(255, 255, 255, 0.4); border-top-color: #fff; border-radius: 50%; animation: sp 0.7s linear infinite; }
@keyframes sp { to { transform: rotate(360deg); } }
.menu { position: absolute; right: 0; top: calc(100% + 6px); z-index: 10; min-width: 200px; padding: 6px; border: 1px solid #d0d7de; border-radius: 8px; background: #fff; box-shadow: 0 8px 24px rgba(31, 35, 40, 0.15); }
.menu-code { min-width: 270px; }
.menu-title { padding: 4px 8px; font-size: 12px; font-weight: 600; color: #636e72; }
.cmd { display: block; margin: 2px 4px 6px; padding: 6px 8px; border-radius: 6px; background: #f6f8fa; font-size: 12px; color: #4a3f8f; }
.menu button { display: block; width: 100%; padding: 7px 8px; border: none; border-radius: 6px; background: none; font-size: 13px; text-align: left; cursor: pointer; }
.menu button:hover { background: #f3f0ff; }
.files { border: 1px solid #d0d7de; border-radius: 6px; overflow: hidden; background: #fff; }
.commit-bar { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: #f6f8fa; border-bottom: 1px solid #d0d7de; font-size: 13px; }
.cm-msg { color: #636e72; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cm-right { margin-left: auto; color: #636e72; font-size: 12px; white-space: nowrap; }
.file-row { display: grid; grid-template-columns: 22px minmax(0, 1.2fr) minmax(0, 1fr) 70px; gap: 8px; align-items: center; padding: 9px 14px; border-top: 1px solid #eaeef2; font-size: 13px; }
.file-row:first-of-type { border-top: none; }
.file-row { cursor: pointer; }
.file-row:hover { background: #f6f8fa; }
.crumb { padding: 8px 14px; border-top: 1px solid #eaeef2; font-size: 13px; color: #636e72; }
.crumb button { border: none; background: none; color: #1264a3; cursor: pointer; font-size: 13px; padding: 0; }
.crumb button:hover { text-decoration: underline; }
.crumb-cnt { float: right; font-size: 12px; }
.item-detail { padding: 12px 14px 14px 44px; border-top: 1px solid #eaeef2; background: #fbfaff; }
.it-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 12px; }
.it-kind { padding: 2px 8px; border-radius: 10px; font-weight: 600; background: #eaeef2; color: #636e72; }
.it-kind.link { background: #e1f0fb; color: #1264a3; }
.it-by { margin-left: auto; color: #636e72; }
.it-sum { margin: 8px 0; font-size: 13px; line-height: 1.6; }
.it-link { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #d0d7de; border-radius: 8px; background: #fff; text-decoration: none; color: #1a1a2e; font-size: 13px; }
.it-link:hover { border-color: #1264a3; }
.it-dom { color: #636e72; font-size: 12px; }
.it-go { margin-left: auto; color: #1264a3; }
.it-slack { padding: 6px 12px; border: 1px solid #d0d7de; border-radius: 6px; background: #fff; font-size: 12px; cursor: pointer; }
.f-name { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.f-count { margin-left: 6px; padding: 0 6px; border-radius: 10px; background: #efeaff; color: #4a3f8f; font-size: 11px; }
.f-msg { color: #636e72; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.f-ago { text-align: right; color: #636e72; font-size: 12px; }
.readme { margin-top: 16px; border: 1px solid #d0d7de; border-radius: 6px; background: #fff; }
.readme-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; border-bottom: 1px solid #d0d7de; font-size: 14px; font-weight: 600; }
.icon-btn { border: none; background: none; cursor: pointer; font-size: 14px; }
.md { padding: 20px 28px 24px; font-size: 14px; line-height: 1.7; color: #1a1a2e; }
.md :deep(h1) { margin: 0 0 12px; padding-bottom: 8px; border-bottom: 1px solid #eaeef2; font-size: 24px; }
.md :deep(h2) { margin: 20px 0 8px; padding-bottom: 6px; border-bottom: 1px solid #eaeef2; font-size: 18px; }
.md :deep(h3) { font-size: 15px; }
.md :deep(p) { margin: 8px 0; }
.md :deep(ul) { margin: 8px 0; padding-left: 22px; }
.md :deep(blockquote) { margin: 8px 0; padding: 2px 14px; border-left: 3px solid #b7a6e6; color: #636e72; }
.md :deep(code) { padding: 1px 6px; border-radius: 4px; background: #f6f8fa; font-size: 12px; }
.md :deep(a) { color: #1264a3; }
.editor { padding: 12px 16px 16px; }
.ed-tabs { display: flex; gap: 4px; margin-bottom: 8px; }
.ed-tabs button { padding: 5px 12px; border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; font-size: 13px; cursor: pointer; }
.ed-tabs button.on { background: #fff; border-color: #6c5ce7; color: #4a3f8f; font-weight: 600; }
.ed-area { width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid #d0d7de; border-radius: 6px; font: 13px/1.6 ui-monospace, Menlo, monospace; resize: vertical; outline: none; }
.ed-area:focus { border-color: #6c5ce7; }
.ed-prev { border: 1px solid #d0d7de; border-radius: 6px; min-height: 200px; }
.ed-foot { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.ed-hint { margin-right: auto; font-size: 12px; color: #8a8fa0; }
.save-btn { padding: 5px 14px; border: 1px solid #1a7f37; border-radius: 6px; background: #1f883d; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.list { border: 1px solid #d0d7de; border-radius: 6px; background: #fff; }
.li { display: flex; align-items: flex-start; gap: 12px; padding: 12px 16px; border-top: 1px solid #eaeef2; }
.li:first-child { border-top: none; }
.li-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; font-size: 14px; }
.li-sub { font-size: 12px; color: #636e72; }
.li-cm { font-size: 12px; color: #636e72; }
.st { padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.st.open { background: #dafbe1; color: #1a7f37; }
.st.closed { background: #eaeef2; color: #636e72; }
.st.merged { background: #e9e0ff; color: #6c3fd1; }
.pr-posts { margin: 6px 0 0; padding: 0; list-style: none; font-size: 12px; color: #636e72; }
.pr-act { display: flex; gap: 6px; }
.merge { padding: 5px 14px; border: 1px solid #1a7f37; border-radius: 6px; background: #1f883d; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.empty { padding: 36px 0; text-align: center; font-size: 13px; color: #636e72; }
.rd-side { display: flex; flex-direction: column; gap: 16px; }
.side-sec { padding-bottom: 16px; border-bottom: 1px solid #eaeef2; }
.side-sec:last-child { border-bottom: none; }
.side-sec h3 { margin: 0 0 10px; font-size: 14px; font-weight: 600; }
.cnt { margin-left: 4px; padding: 0 7px; border-radius: 10px; background: #e3e6ea; font-size: 12px; }
.about { margin: 0 0 10px; font-size: 14px; line-height: 1.5; }
.topics { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.topic { padding: 2px 10px; border-radius: 12px; background: #efeaff; color: #4a3f8f; font-size: 12px; font-weight: 500; }
.stats { margin: 0; padding: 0; list-style: none; font-size: 13px; color: #636e72; display: flex; flex-direction: column; gap: 6px; }
.contrib { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 13px; }
.coh { color: #8a8fa0; font-size: 12px; }
.bar { display: flex; height: 8px; border-radius: 4px; overflow: hidden; }
.bar i { display: block; }
.legend { display: flex; flex-wrap: wrap; gap: 6px 12px; margin-top: 8px; font-size: 12px; color: #636e72; }
.legend i { display: inline-block; width: 8px; height: 8px; margin-right: 5px; border-radius: 50%; }
@media (max-width: 900px) {
  .rd-body { grid-template-columns: 1fr; }
  .file-row { grid-template-columns: 22px minmax(0, 1fr) 70px; }
  .f-msg { display: none; }
}

.li.clickable { cursor: pointer; }
.li.clickable:hover { background: #f6f8fa; }
.back { margin-bottom: 10px; padding: 0; border: none; background: none; font-size: 13px; color: #636e72; cursor: pointer; }
.back:hover { color: #4a3f8f; }
.dt-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.dt-head h2 { margin: 0; font-size: 20px; }
.dt-n { color: #8a8fa0; font-weight: 400; }
.dt-sub { display: flex; align-items: center; gap: 6px; margin: 8px 0 14px; font-size: 13px; color: #636e72; }
.dt-body { margin: 0 0 18px; padding: 14px 16px; border: 1px solid #eaeef2; border-radius: 8px; background: #fff; font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
.dt-act { display: flex; gap: 8px; margin: 14px 0; }
.pr-files .li { align-items: center; font-size: 13px; }
.pr-files span { flex: 1; }
.plus { padding: 1px 8px; border-radius: 10px; background: #dafbe1; color: #1a7f37; font-size: 11px; font-style: normal; }
.form-card { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; padding: 14px; border: 1px solid #d0d7de; border-radius: 6px; background: #fff; }
.form-card input, .form-card textarea { padding: 9px 12px; border: 1px solid #d0d7de; border-radius: 6px; font: 14px/1.6 inherit; outline: none; resize: vertical; }
.form-card input:focus, .form-card textarea:focus { border-color: #6c5ce7; }
.form-foot { display: flex; justify-content: flex-end; gap: 8px; }
.save-btn:disabled { opacity: 0.4; cursor: default; }
.set-sec { margin-bottom: 18px; padding: 16px; border: 1px solid #d0d7de; border-radius: 6px; background: #fff; }
.set-sec h3 { margin: 0 0 10px; font-size: 15px; }
.set-sec.danger { border-color: #f1a9a9; }
.radio { display: block; margin-bottom: 8px; font-size: 13px; line-height: 1.5; }
.set-select { padding: 7px 10px; border: 1px solid #d0d7de; border-radius: 6px; font-size: 14px; }
.set-hint { margin: 8px 0 0; font-size: 12px; color: #8a8fa0; }
.del-btn { margin-top: 8px; padding: 6px 14px; border: 1px solid #d1242f; border-radius: 6px; background: #fff; color: #d1242f; font-size: 13px; font-weight: 600; cursor: pointer; }
.del-btn:hover { background: #d1242f; color: #fff; }
.clone-ov { position: fixed; inset: 0; z-index: 1100; display: flex; align-items: center; justify-content: center; background: rgba(26, 26, 46, 0.35); }
.clone-box { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 26px 40px; border-radius: 14px; background: #fff; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25); font-size: 14px; }
.clone-box p { margin: 0; }
.clone-box code { padding: 3px 10px; border-radius: 6px; background: #f6f8fa; font-size: 12px; color: #4a3f8f; }
</style>
