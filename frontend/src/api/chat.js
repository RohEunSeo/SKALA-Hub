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
  if (!AI_BASE_URL) return { allowed: false, limit: 0, remaining: 0 }
  try {
    const res = await fetch(`${AI_BASE_URL}/api/chat/access`, { headers: authHeaders() })
    if (!res.ok) return { allowed: false, limit: 0, remaining: 0 }
    return await res.json()
  } catch {
    return { allowed: false, limit: 0, remaining: 0 }
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
// 칩 3개는 각각 서버의 검색/키워드/트렌드 경로를 타도록 고른 문구다.
export async function fetchGreeting() {
  return {
    text: 'SKALA Hub에 올라온 글을 찾아서 요약해 드려요.\n어떤 정보를 찾고 계신가요?',
    chips: [
      '이번 달 인기 게시글 추천해줘',
      '요즘 게시글에 가장 많이 올라오는 키워드 알려줘',
      '요즘 뜨는 주제의 글 모아줘',
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
export async function* streamChat({ question, context, category }) {
  if (!AI_BASE_URL) {
    yield { type: 'error', message: 'AI 서버 주소가 설정되지 않았어요. (VITE_AI_API_BASE_URL)' }
    return
  }

  let response
  try {
    response = await fetch(`${AI_BASE_URL}/api/chat`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ question, context, category }),
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
