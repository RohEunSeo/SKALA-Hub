-- 내 폴더: 저장한 글(bookmarks)을 사용자가 직접 분류하는 묶음
--
-- 왜 bookmarks 에 컬럼을 더하는가
--   폴더를 별도 시스템으로 만들면 "저장은 했는데 폴더엔 없네" 가 생긴다.
--   이미 27명이 북마크를 쓰고 있어서(100건) 저장 경로가 둘로 갈라지면 안 된다.
--   폴더는 북마크의 '분류'다. 기존 100건은 folder_id = null, 즉 '미분류'로 그대로 산다.
--   마이페이지 저장 목록은 이 컬럼을 무시하면 지금과 똑같이 동작한다.
--
-- 한 글은 폴더 한 곳에만 둔다 (프론트 localStorage 구현도 map[postId] = folderId 였다).

create table if not exists chat_folders (
    id            bigserial primary key,
    user_slack_id varchar not null references users(slack_id) on delete cascade,
    name          varchar(20) not null,
    color         varchar(9)  not null,   -- '#RRGGBB' (stores/folders.js 의 FOLDER_COLORS)
    sort_order    int not null default 0,
    created_at    timestamptz not null default now(),
    unique (user_slack_id, name)          -- 같은 이름 폴더를 두 개 만들지 못하게
);

create index if not exists idx_chat_folders_user
    on chat_folders (user_slack_id, sort_order);

-- 폴더가 지워져도 저장한 글 자체는 남아야 한다 → set null ('미분류'로 돌아감)
alter table bookmarks
    add column if not exists folder_id bigint references chat_folders(id) on delete set null;

create index if not exists idx_bookmarks_folder on bookmarks (folder_id);
