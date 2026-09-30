// AI 챗봇 API - 지금은 화면 목업용 "가짜 응답 어댑터" (서버 호출 없음).
// 실제 연동 시 이 파일의 함수 시그니처는 그대로 두고 내용만 fetch(POST /api/chat, SSE) 호출로 교체한다.
// status.tool = 단계별 로더 종류: intent(의도 분석) | search_posts(게시글 검색) | think(고민) | compose(답변 정리)
// 이벤트: status(도구 진행 상태) → token(글자 조각) → sources(근거 글) | keywords(키워드 집계) → save_proposal(저장 제안) | clarify(되묻기) → usage → done | error

import { CATEGORIES } from '../constants/categories'

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// 목업용 가짜 게시글 (실제 연동 시 서버가 근거 글 목록을 내려줌)
const FAKE_POSTS = [
  { id: 1, title: '수료 후 이력서에 쓸만한 프로젝트 정리법', category: '자격증·취업', reactions: 24 },
  { id: 2, title: 'SQLD 예상문제 100선 + 실전모의고사 공유드립니다!', category: '교수님', reactions: 43 },
  { id: 3, title: 'RAG 실습 때 쓴 pgvector 세팅 스크립트 공유', category: '학습자료', reactions: 17 },
]

// 목업용 키워드 집계 (실제 연동 시 서버가 게시글 tags/본문에서 집계)
const FAKE_KEYWORDS = [
  { word: 'SQLD', count: 24 },
  { word: 'RAG', count: 17 },
  { word: '이력서', count: 13 },
  { word: 'GPU 서버', count: 9 },
  { word: 'LangChain', count: 8 },
]

export const MOCK_FOLDERS = ['자격증·취업 스크랩', '개발 링크 모음']

// 첫 인사 - 실제로는 LLM 없이 DB 집계(트렌드/내가 자주 저장한 카테고리)로 만들어 비용 0, 한도 차감 없음
export async function fetchGreeting() {
  await wait(200)
  return {
    text: 'SKALA Hub에 올라온 글을 찾고, 요약하고, 내 폴더에 저장해 드려요.\n어떤 정보를 찾고 계신가요?',
    chips: ['이번 달 인기 게시글 추천해줘', '요즘 게시글에 가장 많이 올라오는 키워드 알려줘', '요즘 뜨는 주제의 글 모아줘'],
  }
}

// 질문 1건 처리 - async generator로 이벤트를 하나씩 내보냄 (SSE 스트림 흉내)
export async function* streamChat({ question, context }) {
  // 목업 규칙: '오류' → 에러, '날씨' 등 무관 질문 → 거절, 검색 성격의 질문 → 도구(검색) 경로, 나머지 → 일반 답변
  if (question.includes('오류')) {
    await wait(600)
    yield { type: 'error', message: '잠시 문제가 생겼어요. 다시 시도해 주세요. (횟수는 차감되지 않았어요)' }
    return
  }

  const isOffTopic = /날씨|주식|맛집 아닌|점심 메뉴/.test(question)
  if (isOffTopic) {
    await wait(700)
    yield* typing('저는 스칼라 허브에 올라온 글을 찾고 정리하는 걸 도와드려요. 예를 들어 이렇게 물어봐 주세요.')
    yield { type: 'usage' }
    yield { type: 'done' }
    return
  }

  // 목업 규칙: '키워드/트렌드' 질문 → 집계 도구 경로, 키워드 목록(누르면 그 키워드로 검색)을 내려줌
  if (/키워드|트렌드/.test(question)) {
    await wait(1800)
    yield { type: 'status', tool: 'search_posts', text: `${context}에서 키워드 집계 중` }
    await wait(3000)
    yield { type: 'status', tool: 'compose', text: '답변 정리하는 중' }
    await wait(1800)
    yield* typing('이번 달 게시글에서 가장 많이 언급된 키워드예요. 눌러서 관련 글을 찾아볼 수 있어요.')
    yield { type: 'keywords', items: FAKE_KEYWORDS.map((k) => ({ ...k })) }
    yield { type: 'usage' }
    yield { type: 'done' }
    return
  }

  // 목업 규칙: 주제가 없는 짧은 요청("추천해줘")은 검색 대신 선택지로 되묻기
  const isVague = question.replace(/\s/g, '').length <= 7 && /추천|찾아|알려|글/.test(question) && !/툴|학습|자격|취업|서비스|캠퍼스|SQLD|LLM|RAG|GPU|깃/i.test(question)
  if (isVague) {
    await wait(700)
    yield* typing('조금만 더 알려주세요.')
    yield {
      type: 'clarify',
      question: '어떤 종류의 글을 찾고 있나요?',
      options: CATEGORIES.slice(0, 4).map((c) => ({ label: `${c.icon} ${c.label}`, value: `${c.label} 관련 글 추천해줘` })),
    }
    yield { type: 'done' } // 되묻기는 횟수 차감 없음
    return
  }

  const needsSearch = /찾|추천|글|요약|핫|트렌드|인기/.test(question)
  if (needsSearch) {
    // 게시글 검색/필터 도구가 도는 동안 → 화면은 폴더 속 종이 넘기기 로더
    // 1단계(질문 의도 분석)는 store가 시작 시 표시 → 여기서는 도구 호출 → 답변 정리 순서
    await wait(1800)
    yield { type: 'status', tool: 'search_posts', text: `${context}에서 게시글 검색 중` }
    await wait(3000)
    yield { type: 'status', tool: 'compose', text: '답변 정리하는 중' }
    await wait(1800)
    yield* typing(`${context}에서 관련 글 ${FAKE_POSTS.length}개를 찾았어요.`)
    yield { type: 'sources', posts: FAKE_POSTS.map((p) => ({ ...p })) } // 복사본 - 메시지마다 저장 상태가 따로 놀게
    yield { type: 'save_proposal', folders: MOCK_FOLDERS, postIds: FAKE_POSTS.map((p) => p.id) }
  } else {
    // 도구 없이 답변만 → 새싹 로더
    await wait(1800)
    yield { type: 'status', tool: 'think', text: '고민하는 중' } // 일반 질문은 새싹 모션
    await wait(2400)
    yield { type: 'status', tool: 'compose', text: '답변 정리하는 중' }
    await wait(1800)
    yield* typing('스칼라 허브 글을 기준으로 답해드려요. 궁금한 주제를 알려주시면 관련 글을 찾아볼게요!')
  }
  yield { type: 'usage' }
  yield { type: 'done' }
}

// 글자가 나오는 대로 보여주는 스트리밍 흉내
async function* typing(text) {
  for (const ch of text) {
    yield { type: 'token', text: ch }
    await wait(18)
  }
}
