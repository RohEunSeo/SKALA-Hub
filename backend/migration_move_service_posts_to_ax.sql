-- SKALA 커리큘럼 탭 - "생성형 AI 서비스 개발" 폴더의 하위 카테고리 "서비스 이해·활용" 태그를
-- 폐지하면서, 거기 속해있던 게시글들을 AX 폴더로 옮긴다. 원본 posts는 변경하지 않는다.
UPDATE curriculum_posts
SET stage = 'ax', sub_category = NULL, updated_at = now()
WHERE stage = 'genai' AND sub_category = 'service';
