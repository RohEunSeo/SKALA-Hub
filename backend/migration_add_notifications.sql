-- 알림 기능: 관리자 전체 공지(announcements) + 공지 읽음 추적(announcement_reads) + 개인 알림(notifications)

CREATE TABLE IF NOT EXISTS announcements (
    id BIGSERIAL PRIMARY KEY,
    badge_type varchar(20) NOT NULL DEFAULT '공지',   -- '공지' | '버그' 등, 배지 라벨로 그대로 표시
    title varchar(200) NOT NULL,
    content text,
    created_by varchar(50) REFERENCES users(slack_id), -- 작성한 관리자
    created_at timestamp NOT NULL DEFAULT now(),
    is_deleted boolean NOT NULL DEFAULT false
);

-- 공지 하나당 읽은 유저마다 1행 - "모두 읽음 처리" / 안읽음 배지 카운트의 기준
CREATE TABLE IF NOT EXISTS announcement_reads (
    id BIGSERIAL PRIMARY KEY,
    announcement_id bigint NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id varchar(50) NOT NULL REFERENCES users(slack_id),
    read_at timestamp NOT NULL DEFAULT now(),
    UNIQUE (announcement_id, user_id)
);

-- 개인 알림 - 백엔드 자동 발생(북마크 저장/주간 TOP3)만 존재, 관리자 수동 작성 없음
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id varchar(50) NOT NULL REFERENCES users(slack_id), -- 알림을 받는 사람
    type varchar(30) NOT NULL,        -- 'BOOKMARK_RECEIVED' | 'WEEKLY_TOP3'
    title varchar(200) NOT NULL,
    post_id bigint REFERENCES posts(id) ON DELETE SET NULL, -- 관련 게시글(있으면), 클릭 시 이동용
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications (user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_announcement_reads_user ON announcement_reads (user_id);
