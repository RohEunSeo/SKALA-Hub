-- link_previews 테이블에 "만든 사람" 관리자 수동 입력 컬럼 추가
-- 자동 수집된 게시글 작성자 목록보다 이 값이 있으면 항상 우선 표시됨
ALTER TABLE link_previews ADD COLUMN IF NOT EXISTS admin_creators text;
