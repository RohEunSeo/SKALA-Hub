<script setup>
// 관리자용 챗봇 대화 로그. AdminView 가 1800줄이 넘어 섹션을 여기로 뺐다.
// 권한은 서버가 users.role 로 막는다(미리보기 토글과 무관) - 화면은 결과만 그린다.
import { onMounted, ref } from 'vue'
import { fetchChatLogs } from '../../api/chat'

const PAGE = 30

const items = ref([])
const today = ref(null)
const loading = ref(false)
const error = ref('')
const done = ref(false)
const filter = ref('all') // all | abstained | feedback
const openId = ref(null)

const FB = { 1: '👍', 0: '😐', '-1': '👎' }

async function load({ more = false } = {}) {
  loading.value = true
  error.value = ''
  try {
    const d = await fetchChatLogs({
      limit: PAGE,
      before: more ? items.value.at(-1)?.id : null,
      onlyAbstained: filter.value === 'abstained',
      onlyFeedback: filter.value === 'feedback',
    })
    items.value = more ? [...items.value, ...d.items] : d.items
    if (d.today) today.value = d.today
    done.value = d.items.length < PAGE
  } catch (e) {
    error.value = e.message
  }
  loading.value = false
}

function setFilter(v) {
  if (filter.value === v) return
  filter.value = v
  openId.value = null
  load()
}

// 2026-10-08T13:22:11+09:00 → 10-08 13:22
const when = (iso) => (iso ? iso.slice(5, 16).replace('T', ' ') : '')
const secs = (ms) => (ms == null ? '' : `${(ms / 1000).toFixed(1)}s`)

onMounted(load)
</script>

<template>
  <div class="card">
    <p class="card-desc">
      사용자가 챗봇에 무엇을 물었고 어떤 글을 찾아왔는지 봅니다.
      행을 펼치면 <b>그때 검색된 글</b>이 보입니다 - 답이 왜 틀렸는지는 거기서 드러납니다.
    </p>

    <div v-if="today" class="sum">
      <span>오늘 질문 <b>{{ today.asked }}</b></span>
      <span>사용자 <b>{{ today.users }}</b>명</span>
      <span>LLM 호출 <b>{{ today.llm }}</b></span>
      <span :class="{ warn: today.abstained > 0 }">못 찾음 <b>{{ today.abstained }}</b></span>
      <span :class="{ warn: today.errors > 0 }">에러 <b>{{ today.errors }}</b></span>
      <span>👍{{ today.up }} 😐{{ today.mid }} 👎{{ today.down }}</span>
      <span>평균 <b>{{ secs(today.avg_ms) }}</b></span>
    </div>

    <div class="filters">
      <button :class="{ on: filter === 'all' }" @click="setFilter('all')">전체</button>
      <button :class="{ on: filter === 'abstained' }" @click="setFilter('abstained')">못 찾은 것만</button>
      <button :class="{ on: filter === 'feedback' }" @click="setFilter('feedback')">피드백 있는 것만</button>
      <button class="reload" :disabled="loading" @click="load()">새로고침</button>
    </div>

    <div v-if="error" class="result-box error">{{ error }}</div>
    <div v-else-if="!items.length && !loading" class="result-box">기록이 없습니다.</div>

    <ul class="logs">
      <li v-for="it in items" :key="it.id" :class="{ open: openId === it.id }">
        <button class="row" @click="openId = openId === it.id ? null : it.id">
          <span class="t">{{ when(it.at) }}</span>
          <span class="who">{{ it.user }}<i v-if="it.classNum"> · {{ it.classNum }}</i></span>
          <span class="q">{{ it.question }}</span>
          <span class="tag">{{ it.route }}</span>
          <span v-if="it.abstained" class="tag warn">못 찾음</span>
          <span v-if="it.error" class="tag warn">에러</span>
          <span class="fb">{{ FB[String(it.feedback)] ?? '' }}</span>
          <span class="ms">{{ secs(it.latencyMs) }}</span>
        </button>

        <div v-if="openId === it.id" class="detail">
          <div v-if="it.feedbackReason" class="reason">👎 이유: {{ it.feedbackReason }}</div>
          <div v-if="it.error" class="result-box error">{{ it.error }}</div>
          <pre v-if="it.answer" class="ans">{{ it.answer }}</pre>
          <div v-if="it.sources.length" class="srcs">
            <div class="srcs-h">그때 검색된 글 {{ it.sources.length }}개 <i>(거리가 작을수록 가까움)</i></div>
            <div v-for="s in it.sources" :key="s.id" class="src">
              <span class="score">{{ s.score ?? '-' }}</span>{{ s.title }}
            </div>
          </div>
          <div class="meta">
            모델 {{ it.model ?? '호출 안 함' }} · 범위 {{ it.category ?? '전체' }} · 로그 #{{ it.id }}
          </div>
        </div>
      </li>
    </ul>

    <button v-if="items.length && !done" class="more" :disabled="loading" @click="load({ more: true })">
      {{ loading ? '불러오는 중...' : '더 보기' }}
    </button>
  </div>
</template>

<style scoped>
.sum {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 10px 0 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f4f2fc;
  color: #636e72;
  font-size: 13px;
}
.sum b { color: #4a3f8f; }
.sum .warn b { color: #e0607d; }

.filters { display: flex; gap: 6px; margin-bottom: 12px; }
.filters button {
  padding: 6px 12px;
  border: 1px solid #e0dcf4;
  border-radius: 999px;
  background: #fff;
  color: #636e72;
  font-size: 13px;
  cursor: pointer;
}
.filters button.on { border-color: transparent; background: #4a3f8f; color: #fff; }
.filters .reload { margin-left: auto; }

.logs { margin: 0; padding: 0; list-style: none; }
.logs li { border-top: 1px solid #efedf7; }
.logs li.open { background: #faf9fe; }

.row {
  display: grid;
  grid-template-columns: 82px 128px 1fr auto auto auto 44px;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 9px 4px;
  border: 0;
  background: none;
  text-align: left;
  font-size: 13px;
  color: #1a1a2e;
  cursor: pointer;
}
.row:hover { background: #f7f5fd; }
.t, .ms { color: #8a8f98; font-size: 12px; }
.who { color: #636e72; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.who i { color: #b2bec3; font-style: normal; }
.q { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fb { text-align: center; }

.tag {
  padding: 2px 7px;
  border-radius: 999px;
  background: #eceaf7;
  color: #6b647f;
  font-size: 11px;
  white-space: nowrap;
}
.tag.warn { background: #fdeaef; color: #e0607d; }

.detail { padding: 4px 6px 14px; font-size: 13px; }
.reason { margin-bottom: 8px; color: #e0607d; }
.ans {
  margin: 0 0 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #efedf7;
  color: #1a1a2e;
  font: inherit;
  white-space: pre-wrap;
  word-break: break-word;
}
.srcs-h { margin-bottom: 4px; color: #636e72; font-size: 12px; }
.srcs-h i { color: #b2bec3; font-style: normal; }
.src { display: flex; gap: 8px; padding: 3px 0; color: #636e72; font-size: 12.5px; }
.score { flex: none; width: 46px; color: #8b6fd6; font-variant-numeric: tabular-nums; }
.meta { margin-top: 10px; color: #b2bec3; font-size: 11.5px; }

.more {
  width: 100%;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #e0dcf4;
  border-radius: 10px;
  background: #fff;
  color: #4a3f8f;
  font-size: 13px;
  cursor: pointer;
}
</style>
