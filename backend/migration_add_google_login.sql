-- 구글 계정 연동 로그인: slack_id PK/FK 체계는 그대로 두고, 같은 행에 구글 계정을 매핑만 한다
-- (교육 종료 후 슬랙 계정 비활성화에 대비 - 마이페이지 이력은 slack_id에 그대로 남아있음)

ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id varchar(50) UNIQUE;   -- 구글 OpenID 'sub' 값. UNIQUE라 한 구글 계정은 최대 한 slack_id에만 연결됨 (NULL끼리는 중복 허용)
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_email varchar(255);      -- 마이페이지/관리자 화면에 "연동됨: xxx@gmail.com" 표시용 (로그인 식별자로는 google_id만 사용)
