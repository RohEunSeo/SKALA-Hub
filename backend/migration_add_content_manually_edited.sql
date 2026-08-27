-- 관리자가 본문(content)을 수동으로 고친 게시글은 이후 슬랙 재동기화가 원문으로 다시 덮어쓰지 않도록 표시
ALTER TABLE posts ADD COLUMN IF NOT EXISTS content_manually_edited BOOLEAN NOT NULL DEFAULT false;
