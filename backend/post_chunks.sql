-- 게시글 조각 + 임베딩 (2026-10-05)
-- Supabase SQL Editor에서 실행한다. RLS 경고가 뜨면 "Run and enable RLS"를 누른다
-- (우리 서버는 postgres 계정으로 직접 붙어 RLS를 통과하고, 브라우저는 이 표를 볼 일이 없다).
--
-- 왜 필요한가
--   지금은 임베딩 결과를 ai/data/faiss_index 파일로 들고 있다. Render 무료 플랜은
--   재시작하면 파일이 사라져서 매번 412조각을 다시 임베딩해야 한다(30초·10원).
--   DB에 두면 재시작과 무관하고, 새 글이 올라와도 그 글만 추가하면 된다.
--
-- 설계: **posts의 값을 베끼지 않는다.**
--   category/tags/반응수를 여기 복사해두면, 관리자가 /admin에서 분류를 고쳤을 때
--   옛 값이 박힌 채로 남아 챗봇이 틀린 필터를 건다. 고치려면 412개를 다시 임베딩해야 한다.
--   대신 post_id로 posts를 그때그때 읽는다(JOIN). 그러면
--     - 분류·태그·반응수 수정이 자동 반영되고
--     - is_deleted=true 로 지운 글이 검색에서 자동으로 빠진다
--   여기 남는 건 **조각에만 있는 것**(본문 조각과 그 숫자)뿐이다.
--   본문 자체가 수정되면 그때만 다시 만들면 된다 (숫자가 본문에서 나오므로 불가피).

-- 1) Postgres에 '벡터' 타입과 유사도 연산자를 추가한다. 새 서비스가 아니라 확장 기능이다.
create extension if not exists vector;

create table if not exists post_chunks (
    id          bigserial primary key,
    -- 글이 완전히 지워지면 조각도 같이 지워진다 (슬랙 삭제는 is_deleted라 JOIN에서 거른다)
    post_id     bigint not null references posts(id) on delete cascade,
    chunk_index int  not null default 0,   -- 한 글을 여러 조각으로 자른 경우 몇 번째인지
    chunk_total int  not null default 1,
    content     text not null,             -- 답변을 만들 때 LLM에게 주는 본문 조각

    -- 1536 = text-embedding-3-small의 출력 길이.
    -- 임베딩 모델을 바꾸면 이 숫자도 바뀌고 전체를 다시 만들어야 한다.
    embedding   vector(1536) not null,

    indexed_at  timestamptz not null default now(),
    unique (post_id, chunk_index)          -- 같은 조각이 두 번 들어가지 않게
);

create index if not exists idx_post_chunks_post on post_chunks (post_id);

-- 벡터 전용 인덱스(HNSW)는 **일부러 만들지 않는다.**
-- 조각이 412개뿐이라 전부 훑어도 수 밀리초이고, HNSW는 '근사' 검색이라 정확도를 깎는다.
-- 이 규모에서 근사 검색을 쓰는 건 정확도를 공짜로 버리는 일이다.
-- 글이 수만 개가 되면 그때 아래 한 줄을 추가하면 된다:
--   create index on post_chunks using hnsw (embedding vector_cosine_ops);

-- 확인용 (지금은 0행, 숫자 채우기 전):
--   select count(*) from post_chunks;
