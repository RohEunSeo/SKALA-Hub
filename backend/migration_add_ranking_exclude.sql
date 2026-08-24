-- 특정 게시글을 "가장 반응이 많은 글" 순위보드에서만 제외 (게시글 자체는 삭제되지 않음)
ALTER TABLE posts ADD COLUMN IF NOT EXISTS is_excluded_from_ranking BOOLEAN NOT NULL DEFAULT false;
