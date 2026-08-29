-- SKALA 교육과정 탭: 관리자가 게시글을 커리큘럼 4단계로 수동 큐레이션.
-- 원본 posts는 절대 변경/삭제하지 않고, 이 테이블의 행 추가/수정/제외로만 커리큘럼 탭 노출을 제어한다.
CREATE TABLE IF NOT EXISTS curriculum_posts (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL UNIQUE REFERENCES posts(id),
    stage VARCHAR(30) NOT NULL,
    sub_category VARCHAR(30),
    is_excluded BOOLEAN NOT NULL DEFAULT false,
    added_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_curriculum_posts_stage ON curriculum_posts(stage);
