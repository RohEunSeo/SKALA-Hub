// AI 챗봇 API - Python RAG 서버(ai/)와 SSE로 통신한다.
// 이벤트 형식은 서버의 ai/app/schemas/chat.py 가 보내는 것과 1:1로 맞춰져 있다.
// status(도구 진행) → token(글자 조각) → sources(근거 글) | keywords(키워드 집계)
//   → clarify(되묻기) → usage → done(logId) | error
// save_proposal은 1차 베타에서 서버가 보내지 않는다(폴더가 아직 localStorage라서).
//
// Spring(8080)과는 다른 서버라 주소가 따로다 (VITE_AI_API_BASE_URL).
import { TOKEN_KEY } from '../stores/auth'

const AI_BASE_URL = import.meta.env.VITE_AI_API_BASE_URL

// 로그인 토큰은 Spring이 발급한 것을 그대로 쓴다 (AI 서버가 같은 JWT_SECRET으로 검증)
function authHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY) ?? ''}`,
  }
}

/**
 * 이 사용자에게 챗봇을 열어줄지 + 하루 한도와 남은 횟수.
 *
 * 공개 범위("판교 5반만") 규칙은 서버 .env에만 있다. 화면이 같은 규칙을 또 들고 있으면
 * 서버 설정만 바꿨을 때 조용히 어긋나므로, 판단은 서버에 맡기고 결과만 받는다.
 * 실패하면(서버 꺼짐·네트워크) allowed:false - 눌러도 안 되는 버튼은 안 보이는 게 낫다.
 */
export async function fetchAccess() {
  const 닫힘 = { allowed: false, limit: 0, remaining: 0 }
  if (!AI_BASE_URL) {
    console.warn('[chat] VITE_AI_API_BASE_URL이 없어 챗봇을 숨깁니다.')
    return 닫힘
  }
  try {
    const res = await fetch(`${AI_BASE_URL}/api/chat/access`, { headers: authHeaders() })
    if (!res.ok) {
      console.warn(`[chat] 접근 확인 실패(HTTP ${res.status}) - 챗봇을 숨깁니다.`)
      return 닫힘
    }
    return await res.json()
  } catch {
    // 서버가 안 떠 있을 때 버튼이 소리 없이 사라지면 원인을 찾을 수 없다.
    console.warn(
      `[chat] AI 서버(${AI_BASE_URL})에 연결하지 못해 챗봇을 숨깁니다.\n` +
      '  개발 중이라면:  cd ai && uv run uvicorn main:app --reload --port 8000',
    )
    return 닫힘
  }
}

/**
 * 👍 / 👎 를 서버에 남긴다. logId는 done 이벤트가 알려준 값.
 *
 * 실패해도 화면은 그대로 둔다 - 피드백이 안 저장됐다고 사용자에게 알릴 일은 아니고,
 * 이미 누른 표시를 되돌리면 더 이상하다.
 */
export async function sendFeedback(logId, value, reason = null) {
  if (!AI_BASE_URL || !logId) return false
  try {
    const res = await fetch(`${AI_BASE_URL}/api/chat/${logId}/feedback`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ value, reason }),
    })
    return res.ok
  } catch {
    return false
  }
}

// 첫 인사 - LLM을 쓰지 않아 비용 0, 한도 차감 없음.
//
// 칩은 **지금 실제로 잘 되는 것만** 올린다.
// 예전엔 3개 중 2개가 키워드/트렌드 경로여서 "아직 준비 중이에요"가 나왔다.
// 사용자가 처음 누르는 버튼이 그러면 첫인상이 거기서 끝난다.
//
// 고르는 기준: **검색창으로는 못 찾는 질문**만 올린다.
// "SQLD 자료 추천해줘"는 검색창에 SQLD만 쳐도 나오고, "인기 글"은 홈 순위보드에 이미 있다.
// 그런 걸 칩으로 두면 "검색창 있는데 왜 챗봇?"이라는 질문에 답하지 못한다.
//
// 아래 셋은 전부 검색창(ILIKE) 결과가 **0건**인데 챗봇은 찾아낸다(실측):
//   맥북 처음 세팅할 때…  → +0.46 맥북 생산성 앱 추천
//   혼자 공부하기 힘든데…  → +0.43 가상 스터디룸 / Study Hub
//   점심 뭐 먹을지 고민이야 → +0.37 판교 맛집 리스트
// 말하듯이 물어도 된다는 것도 같이 보여준다.
export async function fetchGreeting() {
  return {
    text: 'SKALA Hub에 올라온 글에서 찾아 드려요.',
    // 할 수 있는 일은 문자열 줄바꿈이 아니라 목록으로 넘긴다.
    // 가운데 정렬된 글 뭉치는 양 끝이 들쭉날쭉해서 읽히지 않는다 - 화면에서 왼쪽 정렬로 그린다.
    items: [
      { icon: '🔎', strong: '주제·키워드로 물으면', rest: '관련된 글을 모아 추천해요' },
      { icon: '📂', strong: '카테고리 탭에서 열면', rest: '그 안에서만 찾아요' },
      { icon: '📄', strong: '글을 열어두고 물으면', rest: '요약하거나 비슷한 글을 찾아요' },
      { icon: '🗂️', strong: '내 폴더를 만들어', rest: '마음에 드는 글을 모아둘 수 있어요' },
    ],
    // 칩은 전부 검색을 돌려 결과를 확인하고 골랐다 (1위 거리 0.32 / 0.43 / 0.44).
    // 이전 칩이던 구어체 질문("점심 뭐 먹을지 고민이야" 0.74)은 거리가 멀어 실제로
    // "못 찾았어요"가 나왔다. 칩은 첫인상이라 되는 것만 둔다.
    chips: [
      '맥북 세팅에 도움이 되는 앱 추천해줘',
      'SKALA랑 AX 관련된 글 찾아줘',
      '학습 자료 중에 RAG랑 에이전트 관련된 글 찾아줘',
    ],
  }
}

/**
 * SSE 본문을 이벤트 하나씩 끊어 읽는다.
 *
 * 서버는 `data: {...}\n\n` 형식으로 보내지만 네트워크는 그 경계를 지켜주지 않는다.
 * 한 이벤트가 두 조각으로 쪼개져 오거나, 두 이벤트가 한 조각에 붙어 올 수 있다.
 * 그래서 버퍼에 쌓아두고 빈 줄(\n\n)을 만날 때마다 잘라낸다.
 */
async function* parseSSE(response) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let cut
      while ((cut = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, cut)
        buffer = buffer.slice(cut + 2)
        const line = chunk.split('\n').find((l) => l.startsWith('data: '))
        if (!line) continue
        try {
          yield JSON.parse(line.slice(6))
        } catch {
          // 깨진 JSON 한 건 때문에 대화 전체가 멈추면 안 된다
        }
      }
    }
  } finally {
    reader.cancel().catch(() => {})
  }
}

/** 질문 1건 처리 - 서버가 보내는 이벤트를 그대로 흘려보낸다. */
export async function* streamChat({ question, context, category, limit, postId }) {
  if (!AI_BASE_URL) {
    yield { type: 'error', message: 'AI 서버 주소가 설정되지 않았어요. (VITE_AI_API_BASE_URL)' }
    return
  }

  let response
  try {
    response = await fetch(`${AI_BASE_URL}/api/chat`, {
      method: 'POST',
      headers: authHeaders(),
      // limit은 "더 찾아볼까요?"를 수락했을 때만 실린다.
      // 값은 서버가 offer로 알려준 것을 그대로 돌려보내는 것 - 화면이 정하지 않는다.
      // postId: 상세 페이지에서 물으면 "지금 이 글"을 알려준다 (요약·비슷한 글에 쓰임)
      body: JSON.stringify({ question, context, category, limit, post_id: postId }),
    })
  } catch {
    // 서버가 꺼져 있거나 네트워크가 끊긴 경우
    yield { type: 'error', message: 'AI 서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.' }
    return
  }

  if (!response.ok) {
    const message = response.status === 401
      ? '로그인이 필요해요. 다시 로그인해 주세요.'
      : '잠시 문제가 생겼어요. 다시 시도해 주세요.'
    yield { type: 'error', message }
    return
  }

  yield* parseSSE(response)
}

/**
 * 관리자용 대화 로그. AI 서버가 chat_logs 를 소유하므로 Spring 이 아니라 여기로 묻는다.
 * 서버가 users.role 을 직접 확인하므로, 화면의 미리보기 토글로는 열리지 않는다.
 */
export async function fetchChatLogs({ limit = 30, before = null, onlyAbstained = false, onlyFeedback = false } = {}) {
  if (!AI_BASE_URL) return { items: [], today: null }
  const q = new URLSearchParams({ limit })
  if (before) q.set('before', before)
  if (onlyAbstained) q.set('only_abstained', 'true')
  if (onlyFeedback) q.set('only_feedback', 'true')
  const res = await fetch(`${AI_BASE_URL}/api/admin/logs?${q}`, { headers: authHeaders() })
  if (!res.ok) throw new Error(res.status === 403 ? '관리자만 볼 수 있습니다' : '로그를 불러오지 못했습니다')
  return res.json()
}
