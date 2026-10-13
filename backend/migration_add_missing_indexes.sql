-- 지금은 글 수가 적어 안 느껴지지만, 다음 컬럼들은 매 동기화/조회마다 조건으로 쓰이는데
-- 색인이 없어서 데이터가 쌓이면 테이블 전체를 훑게 된다. 데이터는 그대로 두고 찾는 속도만 올리는 작업.
-- posts.slack_ts는 UNIQUE 제약(UK)이라 이미 자동으로 색인돼 있어 여기선 건너뜀.

-- 마이페이지 "내가 쓴 글" 등 작성자 기준 조회
CREATE INDEX IF NOT EXISTS idx_posts_user_slack_id ON posts(user_slack_id);

-- 날짜 필터/정렬, 대시보드 히트맵·가입추이 집계
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at);

-- 게시글의 댓글 목록 조회 (replies.post_id FK)
CREATE INDEX IF NOT EXISTS idx_replies_post_id ON replies(post_id);
