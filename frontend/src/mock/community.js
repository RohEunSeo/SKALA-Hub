// 커뮤니티(공유 드라이브/게시판) 프론트 목업 데이터 - 서버 연동 전 디자인 확인용 (가짜 유저/레포)
import { FOLDER_COLORS } from '../stores/folders'

export const MOCK_USERS = {
  hong: { id: 'hong', name: '홍길동', cohort: '4기', color: '#99b8f5' },
  kim: { id: 'kim', name: '김서연', cohort: '4기', color: '#ecb0be' },
  lee: { id: 'lee', name: '이도윤', cohort: '4기', color: '#7cd0c6' },
  park: { id: 'park', name: '박지우', cohort: '4기', color: '#f1c687' },
  choi: { id: 'choi', name: '최민준', cohort: '4기', color: '#b7a6e6' },
  skala: { id: 'skala', name: 'skala-hub', cohort: '공식', color: '#4A3F8F' },
}

const [PINK, YELLOW, MINT, BLUE, PURPLE, CORAL] = FOLDER_COLORS

// files: 카테고리 폴더(dir) 또는 글(doc) - GitHub 파일 표처럼 폴더가 먼저
// 글/링크 항목 - kind: post(슬랙 글) | link(링크 모음 카드), 폴더 안에 담긴 "저장한 것" 한 건
const it = (title, kind, cat, by, ago, summary, url = '') => ({ title, kind, cat, by, ago, summary, url, domain: url ? url.split('/')[2] : '' })
const f = (title, msg, ago, cat, kind = 'post', url = '') => ({ type: 'doc', title, msg, ago, cat, ...it(title, kind, cat, 'kim', ago, `${title}에 대해 정리한 글입니다. 핵심 개념과 실습 예제, 자주 틀리는 포인트를 담고 있어요.`, url) })
const d = (title, msg, ago, items) => ({ type: 'dir', title, msg, ago, items })

export const MOCK_REPOS = [
  {
    id: 'skala-hub/sqld-합격-로드맵', owner: 'skala-hub', name: 'sqld-합격-로드맵', official: true, color: PURPLE,
    desc: 'SQLD 합격까지 필요한 기출·요약·블로그 글을 한 곳에 모은 공식 레포', topics: ['자격증·취업', 'SQLD'],
    visibility: 'Public', stars: 48, watchers: 12, forks: 71, commits: 23, updated: '2일 전',
    contributors: ['skala', 'kim', 'lee'], mode: 'pr',
    cats: [['자격증·취업', 62], ['학습 자료', 30], ['기타', 8]],
    files: [
      d('기출문제', '2023 기출 링크 추가', '2일 전', [
        it('SQLD 2023 기출 해설 모음', 'link', '자격증·취업', 'lee', '2일 전', '회차별 기출 문제와 해설을 정리한 블로그', 'https://velog.io/@sqld/2023-exam'),
        it('SQLD 기출 PDF 공유', 'post', '자격증·취업', 'kim', '1주 전', '3과목 위주로 뽑은 기출 PDF 요약본 공유합니다. 오답 비율 높은 문제에 별표 쳐뒀어요.'),
        it('틀린 문제 오답노트 양식', 'post', '학습 자료', 'hong', '2주 전', '노션으로 만든 오답노트 템플릿. 복제해서 쓰세요.'),
      ]),
      d('요약노트', 'JOIN 정리 글 추가', '1주 전', [
        it('JOIN 종류 한 장 정리', 'link', '학습 자료', 'kim', '1주 전', 'INNER / OUTER / CROSS JOIN을 그림으로 비교', 'https://brunch.co.kr/@dev/join-summary'),
        it('윈도우 함수 쉽게 이해하기', 'post', '학습 자료', 'lee', '2주 전', 'ROW_NUMBER, RANK, DENSE_RANK 차이를 예제로 설명합니다.'),
      ]),
      f('SQLD 3주 합격 후기', '합격 후기 추가', '2주 전', '자격증·취업'),
      f('SQLD 자주 나오는 함수 정리', '함수 정리 추가', '2주 전', '학습 자료', 'link', 'https://tistory.com/sqld-functions'),
    ],
    readme: '# SQLD 합격 로드맵\n\nSKALA 교육생이 **실제로 도움받은 글**만 모았어요.\n\n## 이렇게 쓰세요\n- 기출문제 폴더부터 풀어보기\n- 요약노트로 오답 복습\n- 좋은 글을 발견하면 **Pull request**로 추가 제안!\n\n> 합격하면 후기 글도 남겨주세요 🙌',
    prs: [
      { n: 3, title: 'SQLD 기출 해설 블로그 3개 추가', by: 'lee', state: 'open', ago: '3시간 전', posts: ['SQLD 2024 기출 해설 (1)', 'SQLD 2024 기출 해설 (2)', 'SQLD 오답노트 템플릿'] },
      { n: 2, title: '중복 링크 정리', by: 'kim', state: 'merged', ago: '4일 전', posts: ['중복된 2개 글 제거'] },
    ],
    issues: [
      { n: 5, title: '3과목 SQL 활용 자료가 부족해요', by: 'park', state: 'open', ago: '1일 전', comments: 4 },
      { n: 1, title: 'ERD 설명 잘 된 글 추천해주세요', by: 'hong', state: 'closed', ago: '1주 전', comments: 2 },
    ],
  },
  {
    id: 'kim/rag-실습-모음', owner: 'kim', name: 'rag-실습-모음', official: false, color: BLUE,
    desc: 'RAG 파이프라인 만들 때 참고한 튜토리얼·깃허브 코드 모음', topics: ['학습 자료', '개발 툴·환경'],
    visibility: 'Public', stars: 31, watchers: 7, forks: 34, commits: 12, updated: '어제',
    contributors: ['kim', 'park'], mode: 'open',
    cats: [['학습 자료', 55], ['개발 툴·환경', 45]],
    files: [
      d('임베딩', '임베딩 비교 글 추가', '어제', [
        it('한국어 임베딩 모델 비교', 'link', '학습 자료', 'kim', '어제', 'bge-m3, KoSimCSE 등 한국어 검색 성능 벤치마크', 'https://huggingface.co/blog/ko-embeddings'),
        it('문장 임베딩 직접 만들어보기', 'link', '개발 툴·환경', 'park', '4일 전', 'sentence-transformers 실습 코드', 'https://github.com/example/embedding-lab'),
      ]),
      d('벡터DB', 'pgvector 가이드 추가', '3일 전', [
        it('Supabase pgvector 시작하기', 'link', '개발 툴·환경', 'kim', '3일 전', 'Supabase에서 벡터 검색 켜는 방법', 'https://supabase.com/docs/guides/ai'),
      ]),
      f('LangChain 없이 RAG 만들기', '첫 글 추가', '1주 전', '학습 자료'),
    ],
    readme: '# RAG 실습 모음\n\n프로젝트하면서 **직접 써본 자료**만 담았어요.\n\n- 임베딩 → 벡터DB → 검색 순서로 보면 좋아요\n- 누구나 바로 추가할 수 있어요 (승인 없이 반영)',
    prs: [], issues: [{ n: 2, title: 'Chroma 말고 pgvector 자료도 있나요?', by: 'hong', state: 'open', ago: '2일 전', comments: 3 }],
  },
  {
    id: 'lee/판교-맛집-지도', owner: 'lee', name: '판교-맛집-지도', official: false, color: CORAL,
    desc: '점심·회식 장소 후기 모음 (구내식당 대신 갈 곳)', topics: ['기타', '맛집'],
    visibility: 'Public', stars: 22, watchers: 5, forks: 40, commits: 31, updated: '5시간 전',
    contributors: ['lee', 'kim', 'hong', 'park'], mode: 'open',
    cats: [['기타', 100]],
    files: [d('한식', '백현동 맛집 추가', '5시간 전', [
        it('백현동 순두부 맛집', 'post', '기타', 'lee', '5시간 전', '점심 11:30 전에 가면 줄 없어요. 순두부 + 계란말이 추천.'),
        it('판교 칼국수 골목 후기', 'post', '기타', 'hong', '3일 전', '캠퍼스에서 도보 7분, 가격 9천원.'),
      ]), d('카페', '조용한 카페 3곳', '2일 전', [
        it('스터디하기 좋은 카페 3곳', 'link', '기타', 'park', '2일 전', '콘센트/와이파이/소음 기준으로 정리', 'https://naver.me/cafe-study'),
      ]), f('판교 점심 TOP10', 'TOP10 추가', '1주 전', '기타')],
    readme: '# 판교 맛집 지도\n\n**줄 안 서는 곳** 위주로 정리했어요.\n\n- 한식 / 카페 폴더로 구분\n- 새로운 곳 발견하면 바로 추가해주세요!',
    prs: [], issues: [],
  },
  {
    id: '@me/프론트-면접-준비', owner: '@me', name: '프론트-면접-준비', official: false, color: YELLOW,
    desc: 'Vue/React 면접 질문과 CS 기초 정리 글 모음', topics: ['자격증·취업', '개발 툴·환경'],
    visibility: 'Public', stars: 17, watchers: 3, forks: 19, commits: 9, updated: '3일 전',
    contributors: ['me'], mode: 'pr',
    cats: [['자격증·취업', 70], ['학습 자료', 30]],
    files: [d('Vue', 'Vue3 반응성 질문', '3일 전', [
        it('Vue3 반응성 원리 면접 질문', 'post', '자격증·취업', 'me', '3일 전', 'ref vs reactive, Proxy 동작 방식 질문 모음'),
        it('Pinia 상태관리 정리', 'link', '개발 툴·환경', 'me', '1주 전', 'setup store 패턴 정리', 'https://pinia.vuejs.org'),
      ]), d('CS기초', '네트워크 정리', '5일 전', [
        it('HTTP/HTTPS 면접 대비 정리', 'link', '자격증·취업', 'me', '5일 전', 'TLS 핸드셰이크까지 한 번에', 'https://velog.io/@cs/http'),
      ]), f('기술면접 후기 모음', '후기 추가', '1주 전', '자격증·취업')],
    readme: '# 프론트 면접 준비\n\n면접 전날 **훑어보는 용도**로 만들었어요.',
    prs: [{ n: 1, title: 'React 질문 폴더 추가', by: 'choi', state: 'open', ago: '어제', posts: ['React 면접 질문 20선'] }], issues: [],
  },
  {
    id: 'choi/깃-충돌-해결', owner: 'choi', name: '깃-충돌-해결', official: false, color: MINT,
    desc: 'merge conflict 났을 때 도움 된 글들', topics: ['개발 툴·환경', '오류 해결'],
    visibility: 'Public', stars: 9, watchers: 2, forks: 11, commits: 5, updated: '2주 전',
    contributors: ['choi', 'lee'], mode: 'open',
    cats: [['개발 툴·환경', 80], ['기타', 20]],
    files: [f('rebase vs merge 한 번에 정리', '글 추가', '2주 전', '개발 툴·환경'), f('conflict 해결 실전', '글 추가', '2주 전', '기타')],
    readme: '# 깃 충돌 해결\n\n팀 프로젝트 때 **살려준** 글들입니다.', prs: [], issues: [],
  },
]

// ===== 게시판 =====
// type: notice(공지) | request(정보요청) | tip(꿀팁) | qna(Q&A) | free(자유)
export const BOARD_TYPES = [
  { value: 'notice', label: '공지', icon: '📢', adminOnly: true },
  { value: 'request', label: '정보요청', icon: '🙋' },
  { value: 'tip', label: '꿀팁', icon: '💡' },
  { value: 'qna', label: 'Q&A', icon: '❓' },
  { value: 'free', label: '자유', icon: '💬' },
]

export const MOCK_BOARD = [
  { id: 'b1', type: 'notice', pinned: true, title: '커뮤니티 게시판이 열렸어요 — 이용 안내', by: 'skala', ago: '2일 전', likes: 21,
    body: '교육생끼리 자유롭게 질문하고 정보를 나누는 공간입니다.\n\n- 정보 요청: 필요한 자료를 요청하면 **레포로 답변**해 줄 수 있어요\n- 꿀팁: 유용했던 글/레포를 소개해 주세요\n- 서로 존중하는 말투를 써주세요', attach: null },
  { id: 'b2', type: 'request', title: 'SQLD 3과목 SQL 활용 자료 찾아요', by: 'park', ago: '5시간 전', likes: 3, solved: false,
    body: '3과목 SQL 활용 쪽이 약해서 정리 잘 된 자료 있으면 추천 부탁드려요. 기출 풀이도 좋아요!', attach: null },
  { id: 'b3', type: 'request', title: 'RAG 평가 지표(RAGAS) 정리된 글 있나요?', by: 'hong', ago: '어제', likes: 5, solved: true,
    body: '프로젝트에서 RAG 성능을 숫자로 보여줘야 하는데 RAGAS 쓰는 법 정리된 자료 구해요.', attach: null },
  { id: 'b4', type: 'tip', title: '면접 준비할 때 이 레포 정말 도움 됐어요', by: 'kim', ago: '2일 전', likes: 14,
    body: '기술면접 전날 훑어보기 좋아서 공유합니다. **CS기초 폴더**가 특히 알짜예요.', attach: { kind: 'repo', id: '@me/프론트-면접-준비' } },
  { id: 'b5', type: 'tip', title: '판교 점심 줄 안 서는 곳 모았어요', by: 'lee', ago: '3일 전', likes: 18,
    body: '구내식당 질릴 때 가기 좋은 곳이에요. 후기 추가는 누구나 바로 가능!', attach: { kind: 'repo', id: 'lee/판교-맛집-지도' } },
  { id: 'b6', type: 'qna', title: 'Vue에서 Pinia store 초기화는 어떻게 하나요?', by: 'choi', ago: '4일 전', likes: 2, solved: false,
    body: '로그아웃할 때 모든 store를 초기 상태로 되돌리고 싶은데 `$reset`이 setup store에서는 안 되네요.', attach: null },
  { id: 'b7', type: 'free', title: '다들 프로젝트 팀 회고는 어떻게 하세요?', by: 'hong', ago: '5일 전', likes: 7,
    body: 'KPT 말고 다른 방식 써보신 분 있나요? 이번엔 좀 다르게 해보고 싶어서요.', attach: null },
]

export const MOCK_COMMENTS = {
  b2: [{ id: 'c1', by: 'lee', ago: '3시간 전', text: '제가 만든 로드맵 레포에 3과목 폴더 있어요! 한번 보시고 부족하면 PR 주세요 🙌', attach: { kind: 'repo', id: 'skala-hub/sqld-합격-로드맵' } }],
  b3: [{ id: 'c2', by: 'kim', ago: '어제', text: 'RAG 실습 모음 레포 임베딩/평가 쪽 글 정리해뒀어요.', attach: { kind: 'repo', id: 'kim/rag-실습-모음' }, adopted: true }],
  b6: [
    { id: 'c3', by: 'kim', ago: '4일 전', text: 'setup store는 `$reset`이 없어서 초기값을 함수로 만들어 두고 직접 되돌려야 해요.' },
    { id: 'c4', by: 'lee', ago: '4일 전', text: '저는 `$patch`로 초기 객체를 덮어쓰는 방식으로 했어요.', parent: 'c3' },
  ],
  b7: [{ id: 'c5', by: 'park', ago: '5일 전', text: '4Ls(Liked/Learned/Lacked/Longed for) 추천해요!' }],
}
