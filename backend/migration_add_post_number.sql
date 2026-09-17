-- 게시글이 SKALA Hub 기준 몇 번째 글인지 (삭제 안 된 글 기준 누적 순번) - 생성 시점에 한 번만 계산해서
-- 영구 저장. 매번 재계산하지 않아 조회 성능에 영향 없고, 표시되는 번호가 이후 삭제/추가로 바뀌지 않음
ALTER TABLE posts ADD COLUMN IF NOT EXISTS post_number integer;

-- 기존에 이미 동기화된 글들도 작성 시각(created_at) 오름차순으로 순번을 매겨서 채워줌 (삭제된 글은 제외)
UPDATE posts p
SET post_number = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at ASC) AS rn
    FROM posts
    WHERE is_deleted = false
) sub
WHERE p.id = sub.id;
