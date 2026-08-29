-- 게시글 AI 한 줄 제목/요약. NULL이면 "아직 생성 안 됨"으로 취급(카테고리 분류의 category IS NULL과 동일한 방식).
ALTER TABLE posts ADD COLUMN IF NOT EXISTS ai_title VARCHAR(200);
