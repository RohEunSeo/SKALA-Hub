-- SKALA 커리큘럼 탭 AX(AI Transformation) 폴더 - 기존 게시글 중 AX 관련 글을 일괄 매칭해서
-- curriculum_posts에 stage='ax'로 등록. 원본 posts는 전혀 변경하지 않고, 이미 다른 stage에
-- 큐레이션된 게시글은 건드리지 않는다(post_id UNIQUE).
--
-- 실행 순서: 1단계 SELECT로 매칭 결과를 먼저 눈으로 확인 → 이상 없으면 2단계 INSERT 실행.
--
-- (?<![a-z])ax(?![a-z]) : 'ax' 앞뒤로 영문 알파벳이 붙어있지 않을 때만 매칭.
--   - MAX, TAX, SYNTAX, AXIOS 등 다른 단어의 일부인 경우는 제외
--   - "AX가", "AX는", "AX팀"처럼 한글 조사/단어가 공백 없이 바로 붙는 경우는 포함
--     (한글은 [a-z]가 아니므로 뒤쪽 경계 조건을 통과함)

-- 1단계: 미리보기
SELECT id, user_name, left(content, 80) AS preview, created_at
FROM posts
WHERE is_deleted = false
  AND (
    content ~* '(?<![a-z])ax(?![a-z])'
    OR content ILIKE '%AI Transformation%'
    OR content ILIKE '%AI 전환%'
    OR content ILIKE '%AI전환%'
    OR content ILIKE '%에이엑스%'
  )
ORDER BY created_at DESC;

-- 2단계: 반영 (1단계 결과 확인 후 실행)
INSERT INTO curriculum_posts (post_id, stage, added_by, created_at, updated_at)
SELECT p.id, 'ax', 'system_ax_filter', now(), now()
FROM posts p
WHERE p.is_deleted = false
  AND (
    p.content ~* '(?<![a-z])ax(?![a-z])'
    OR p.content ILIKE '%AI Transformation%'
    OR p.content ILIKE '%AI 전환%'
    OR p.content ILIKE '%AI전환%'
    OR p.content ILIKE '%에이엑스%'
  )
ON CONFLICT (post_id) DO NOTHING;
