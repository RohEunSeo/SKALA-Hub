-- 공지 하나에 이동 경로를 최대 2개까지 걸 수 있도록 커스텀 라벨 + 두 번째 링크 컬럼 추가
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS link_label varchar(50);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS link_label_2 varchar(50);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS link_path_2 varchar(300);
