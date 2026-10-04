-- 챗봇 질문 로그 (2026-10-05)
-- Supabase SQL Editor에서 직접 실행한다 (이 프로젝트는 마이그레이션을 수동 적용한다).
--
-- 왜 필요한가 - 목적 3개
--  1) 평가셋 재료: 슬랙 채널엔 질문이 거의 없다(사람 댓글 281개 중 질문 12개, 진짜 정보탐색 2개).
--     실제 질문은 챗봇을 열어서 받는 수밖에 없고, 저장하지 않으면 아무것도 남지 않는다.
--  2) 일일 한도: 지금은 프론트가 혼자 세고 있어 새로고침하면 리셋된다 (chat.py가 항상 usage(10)을 보냄).
--  3) 비용·품질 모니터링: 토큰·지연·피드백.

create table if not exists chat_logs (
    id                 bigserial primary key,
    -- 유저가 지워져도 로그는 남긴다 (분석용). 그래서 cascade가 아니라 set null.
    user_slack_id      varchar references users(slack_id) on delete set null,
    -- 다른 테이블(posts/replies)은 timestamp를 쓰는데 여기만 timestamptz다.
    -- "하루"의 경계를 한국 시간으로 정확히 계산해야 하기 때문. 시간으로 JOIN하는 곳이 없어 안전하다.
    created_at         timestamptz not null default now(),

    -- 질문 ---------------------------------------------------------------
    question           text not null,
    route              varchar,          -- search/popular/keywords/about/how/reject/clarify
    category           varchar,          -- 질문한 탭. null이면 전체 피드

    -- 검색 결과 -----------------------------------------------------------
    retrieved_post_ids bigint[],
    -- FAISS는 L2 거리를 준다 (작을수록 가까움, 0이면 완전 동일).
    -- 정규화된 벡터에서 cosine = 1 - 거리^2/2 로 환산해 읽는다.
    -- 환산값이 아니라 원값을 넣는다 - 파생값은 언제든 계산되지만 원값은 복원이 안 된다.
    retrieved_scores   real[],

    -- 답변 ---------------------------------------------------------------
    answer             text,
    -- "올라온 글 중에는 찾지 못했어요"라고 답했나.
    -- true인 행이 곧 '답이 없는 질문' 후보다 - 평가셋에서 가장 만들기 어려운 20%가 여기 쌓인다.
    abstained          boolean not null default false,

    -- 운영 ---------------------------------------------------------------
    -- 한도는 route가 아니라 '돈이 든 호출' 기준으로 센다.
    -- 고정 문구(about/how/reject)는 LLM을 안 부르므로 차감하지 않는다.
    -- route에서 유도할 수도 있지만, 나중에 LLM 라우팅으로 바꾸면 그 유도 규칙이 깨진다.
    llm_called         boolean not null default false,
    model              varchar,          -- 모델은 환경변수 한 줄로 바뀐다
    index_version      varchar,          -- 인덱스는 재빌드로 바뀐다. 둘은 따로 움직여서 컬럼도 따로 둔다
    latency_ms         integer,
    prompt_tokens      integer,          -- 스트리밍이라 못 받을 수 있다 (null 허용)
    completion_tokens  integer,
    error              text,

    -- 피드백 (답변 후 UPDATE로 채운다) --------------------------------------
    feedback           smallint check (feedback in (-1, 1)),   -- 👍 1 / 👎 -1 / null 무응답
    -- 👎를 누르면 화면이 이유를 묻는다('관련 없는 글이에요' 등). 어떤 실패가 잦은지가
    -- 개선 우선순위를 정해주므로 버리지 않고 같이 남긴다.
    feedback_reason    text,
    feedback_at        timestamptz
);

-- 일일 한도 조회용. 질문마다 돌아가므로 반드시 있어야 한다.
create index if not exists idx_chat_logs_user_time on chat_logs (user_slack_id, created_at desc);
-- 로그 훑어보기용
create index if not exists idx_chat_logs_time on chat_logs (created_at desc);

-- 확인용 (실행 후):
--   select count(*) from chat_logs;
--   select created_at, question, route, abstained, answer from chat_logs order by created_at desc limit 20;

-- 이미 테이블을 만든 뒤라면 이 한 줄만 추가로 실행한다:
--   alter table chat_logs add column if not exists feedback_reason text;
