-- 전체 공지에 클릭 시 이동 경로(link_path), 수정 시각(updated_at) 컬럼 추가
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS link_path varchar(300);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS updated_at timestamp;
