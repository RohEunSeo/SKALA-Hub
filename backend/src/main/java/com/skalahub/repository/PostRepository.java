// Post 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Post;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findBySlackTs(String slackTs);

    // 로컬 환경(FRONTEND_URL=localhost)에서 동기화되어 슬랙 알림이 보류된 게시글 - 관리자 모드 "대기" 목록
    List<Post> findByPendingNotificationTrue();

    // 순위보드에서 제외된 게시글 - 관리자 전용 "제외된 글 보기" 패널, 복원 가능하도록 목록으로 반환
    List<Post> findByIsExcludedFromRankingTrueAndIsDeletedFalseOrderByReactionCountDesc();

    // 카테고리/태그/키워드/작성자/기간/캠퍼스 조건은 값이 없으면(null) 무시. 정렬은 sort 값에 따라 동적 분기
    @Query(
            value = """
            SELECT * FROM posts p
            WHERE p.is_deleted = false
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
              AND (CAST(:keyword AS varchar) IS NULL
                   OR p.content ILIKE CONCAT('%', CAST(:keyword AS varchar), '%')
                   OR p.user_name ILIKE CONCAT('%', CAST(:keyword AS varchar), '%'))
              AND (CAST(:author AS varchar) IS NULL OR p.user_name ILIKE CONCAT('%', CAST(:author AS varchar), '%'))
              AND (CAST(:dateFrom AS timestamp) IS NULL OR p.created_at >= CAST(:dateFrom AS timestamp))
              AND (CAST(:dateTo AS timestamp) IS NULL OR p.created_at < CAST(:dateTo AS timestamp))
              AND (CAST(:campusActive AS boolean) = false OR p.user_slack_id IN (:campusSlackIds))
              AND (CAST(:hasLink AS boolean) IS NULL OR CAST(:hasLink AS boolean) = false
                   OR p.attachments IS NOT NULL OR p.content ~ '<https?://')
            ORDER BY
              CASE WHEN CAST(:sort AS varchar) = 'popular' THEN p.reaction_count END DESC NULLS LAST,
              CASE WHEN CAST(:sort AS varchar) = 'saved' THEN p.bookmark_count END DESC NULLS LAST,
              CASE WHEN CAST(:sort AS varchar) = 'oldest' THEN p.created_at END ASC,
              CASE WHEN CAST(:sort AS varchar) = 'oldest' THEN NULL ELSE p.created_at END DESC
            """,
            countQuery = """
            SELECT count(*) FROM posts p
            WHERE p.is_deleted = false
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
              AND (CAST(:keyword AS varchar) IS NULL
                   OR p.content ILIKE CONCAT('%', CAST(:keyword AS varchar), '%')
                   OR p.user_name ILIKE CONCAT('%', CAST(:keyword AS varchar), '%'))
              AND (CAST(:author AS varchar) IS NULL OR p.user_name ILIKE CONCAT('%', CAST(:author AS varchar), '%'))
              AND (CAST(:dateFrom AS timestamp) IS NULL OR p.created_at >= CAST(:dateFrom AS timestamp))
              AND (CAST(:dateTo AS timestamp) IS NULL OR p.created_at < CAST(:dateTo AS timestamp))
              AND (CAST(:campusActive AS boolean) = false OR p.user_slack_id IN (:campusSlackIds))
              AND (CAST(:hasLink AS boolean) IS NULL OR CAST(:hasLink AS boolean) = false
                   OR p.attachments IS NOT NULL OR p.content ~ '<https?://')
            """,
            nativeQuery = true)
    Page<Post> search(
            @Param("category") String category,
            @Param("tag") String tag,
            @Param("keyword") String keyword,
            @Param("author") String author,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("campusActive") boolean campusActive,
            @Param("campusSlackIds") List<String> campusSlackIds,
            @Param("hasLink") Boolean hasLink,
            @Param("sort") String sort,
            Pageable pageable);

    @Query(value = "SELECT MAX(synced_at) FROM posts", nativeQuery = true)
    LocalDateTime findLastSyncedAt();

    @Query(value = "SELECT count(*) FROM posts WHERE is_deleted = false AND created_at >= :from", nativeQuery = true)
    long countCreatedSince(@Param("from") LocalDateTime from);

    long countByIsDeletedFalse();

    @Query(
            value =
                    "SELECT category, count(*) FROM posts WHERE is_deleted = false AND category IS NOT NULL GROUP BY category",
            nativeQuery = true)
    List<Object[]> countByCategory();

    // 대시보드 게시글 기여도 히트맵 - 날짜별 게시글 수
    @Query(
            value =
                    "SELECT DATE(created_at) AS d, count(*) FROM posts WHERE is_deleted = false AND created_at IS NOT NULL GROUP BY DATE(created_at)",
            nativeQuery = true)
    List<Object[]> countByCreatedAtDate();

    // 대시보드 카테고리별 명예의 전당 Top3 - 반응 수 기준 (교육생 서비스도 투표 기능 없이 동일 기준)
    @Query(
            value = """
            SELECT * FROM posts p
            WHERE p.is_deleted = false
              AND p.reaction_count > 0
              AND p.is_excluded_from_ranking = false
              AND p.category = :category
            ORDER BY p.reaction_count DESC
            LIMIT 3
            """,
            nativeQuery = true)
    List<Post> findTopReactionsByCategory(@Param("category") String category);

    // 학습자료 하위 태그(영상/블로그·글/깃허브)별 게시글 수 - 사이드바 하위 카테고리 개수 표시용
    @Query(
            value =
                    "SELECT tag, count(*) FROM posts, unnest(tags) AS tag WHERE is_deleted = false GROUP BY tag",
            nativeQuery = true)
    List<Object[]> countByTag();

    long countByUserSlackIdAndIsDeletedFalse(String userSlackId);

    // 마이페이지 "내가 올린 글" - category/tag는 값이 없으면(null) 무시
    @Query(
            value = """
            SELECT * FROM posts p
            WHERE p.user_slack_id = :slackId AND p.is_deleted = false
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
            ORDER BY p.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM posts p
            WHERE p.user_slack_id = :slackId AND p.is_deleted = false
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
            """,
            nativeQuery = true)
    Page<Post> findMyPosts(
            @Param("slackId") String slackId,
            @Param("category") String category,
            @Param("tag") String tag,
            Pageable pageable);

    @Query(
            value = "SELECT COALESCE(SUM(reaction_count), 0) FROM posts WHERE user_slack_id = :slackId AND is_deleted = false",
            nativeQuery = true)
    long sumReactionsByUser(@Param("slackId") String slackId);

    // 마이페이지 카테고리 필터 탭의 "(N)" 표시용 - "내가 올린 글" 카테고리/태그별 개수
    @Query(
            value =
                    "SELECT category, count(*) FROM posts WHERE user_slack_id = :slackId AND is_deleted = false AND category IS NOT NULL GROUP BY category",
            nativeQuery = true)
    List<Object[]> countMyPostsByCategory(@Param("slackId") String slackId);

    @Query(
            value =
                    "SELECT tag, count(*) FROM posts, unnest(tags) AS tag WHERE user_slack_id = :slackId AND is_deleted = false GROUP BY tag",
            nativeQuery = true)
    List<Object[]> countMyPostsByTag(@Param("slackId") String slackId);

    // 마이페이지 "반응한 글" - 내가 이모지를 남긴 게시글 목록. category/tag는 값이 없으면(null) 무시
    @Query(
            value = """
            SELECT * FROM posts p
            WHERE p.is_deleted = false AND :slackId = ANY(p.reacted_user_ids)
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
            ORDER BY p.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM posts p
            WHERE p.is_deleted = false AND :slackId = ANY(p.reacted_user_ids)
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
            """,
            nativeQuery = true)
    Page<Post> findReactedByUser(
            @Param("slackId") String slackId,
            @Param("category") String category,
            @Param("tag") String tag,
            Pageable pageable);

    @Query(
            value = "SELECT count(*) FROM posts WHERE is_deleted = false AND :slackId = ANY(reacted_user_ids)",
            nativeQuery = true)
    long countReactedByUser(@Param("slackId") String slackId);

    // 마이페이지 카테고리 필터 탭의 "(N)" 표시용 - "반응한 글" 카테고리/태그별 개수
    @Query(
            value =
                    "SELECT category, count(*) FROM posts WHERE is_deleted = false AND :slackId = ANY(reacted_user_ids) AND category IS NOT NULL GROUP BY category",
            nativeQuery = true)
    List<Object[]> countReactedByCategory(@Param("slackId") String slackId);

    @Query(
            value =
                    "SELECT tag, count(*) FROM posts, unnest(tags) AS tag WHERE is_deleted = false AND :slackId = ANY(reacted_user_ids) GROUP BY tag",
            nativeQuery = true)
    List<Object[]> countReactedByTag(@Param("slackId") String slackId);

    // 홈 화면 순위보드 - 반응/댓글 많은 순 TOP3 (dateFrom이 null이면 전체 기간, 아니면 그 시점 이후만)
    @Query(
            value = """
            SELECT * FROM posts p
            WHERE p.is_deleted = false
              AND p.reaction_count > 0
              AND p.is_excluded_from_ranking = false
              AND (CAST(:dateFrom AS timestamp) IS NULL OR p.created_at >= CAST(:dateFrom AS timestamp))
            ORDER BY p.reaction_count DESC
            LIMIT 3
            """,
            nativeQuery = true)
    List<Post> findTopReactions(@Param("dateFrom") LocalDateTime dateFrom);

    // 댓글 랭킹 진입 조건은 교육생(비봇) 댓글이 1개 이상 있어야 하고(그래야 동기화 안내 댓글 1개만 달린
    // 글이 바로 랭킹에 뜨는 걸 막음), 일단 랭킹에 뜨면 표시/정렬은 화면과 동일하게 봇 댓글 포함 전체 수(reply_count)를 쓴다
    @Query(
            value = """
            SELECT p.id AS id, p.reply_count AS comment_count
            FROM posts p
            WHERE p.is_deleted = false
              AND p.is_excluded_from_ranking = false
              AND (CAST(:dateFrom AS timestamp) IS NULL OR p.created_at >= CAST(:dateFrom AS timestamp))
              AND EXISTS (
                  SELECT 1 FROM replies r
                  WHERE r.post_id = p.id
                    AND r.content NOT LIKE CONCAT('%', CAST(:successMarker AS varchar), '%')
                    AND r.content NOT LIKE CONCAT('%', CAST(:failureMarker AS varchar), '%')
              )
            ORDER BY comment_count DESC
            LIMIT 3
            """,
            nativeQuery = true)
    List<Object[]> findTopComments(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("successMarker") String successMarker,
            @Param("failureMarker") String failureMarker);

    // 저장(북마크) 많이 된 순 TOP3 - [post_id, save_count] 배열 목록
    @Query(
            value = """
            SELECT p.id AS id, count(b.id) AS save_count FROM posts p
            JOIN bookmarks b ON b.post_id = p.id
            WHERE p.is_deleted = false
              AND p.is_excluded_from_ranking = false
              AND (CAST(:dateFrom AS timestamp) IS NULL OR p.created_at >= CAST(:dateFrom AS timestamp))
            GROUP BY p.id
            ORDER BY save_count DESC
            LIMIT 3
            """,
            nativeQuery = true)
    List<Object[]> findTopSaveCounts(@Param("dateFrom") LocalDateTime dateFrom);

    // 관리자 - 미분류 게시글 (일괄 분류용 전체 목록 / 화면 표시용 페이지 목록)
    @Query(
            value = "SELECT * FROM posts WHERE is_deleted = false AND (category IS NULL OR category = '') ORDER BY created_at DESC",
            nativeQuery = true)
    List<Post> findAllUncategorized();

    @Query(
            value = "SELECT * FROM posts WHERE is_deleted = false AND (category IS NULL OR category = '') ORDER BY created_at DESC",
            countQuery = "SELECT count(*) FROM posts WHERE is_deleted = false AND (category IS NULL OR category = '')",
            nativeQuery = true)
    Page<Post> findUncategorized(Pageable pageable);

    // 관리자 - AI 제목이 아직 없는 게시글 (일괄 생성용)
    @Query(value = "SELECT * FROM posts WHERE is_deleted = false AND ai_title IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Post> findAllMissingAiTitle();

    @Query(value = "SELECT count(*) FROM posts WHERE is_deleted = false AND ai_title IS NULL", nativeQuery = true)
    long countMissingAiTitle();
}
