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

    // 카테고리/태그/키워드/작성자/기간 조건은 값이 없으면(null) 무시 - 최신순 고정
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
            ORDER BY p.created_at DESC
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
            """,
            nativeQuery = true)
    Page<Post> search(
            @Param("category") String category,
            @Param("tag") String tag,
            @Param("keyword") String keyword,
            @Param("author") String author,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
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

    // 학습자료 하위 태그(영상/블로그·글/깃허브)별 게시글 수 - 사이드바 하위 카테고리 개수 표시용
    @Query(
            value =
                    "SELECT tag, count(*) FROM posts, unnest(tags) AS tag WHERE is_deleted = false GROUP BY tag",
            nativeQuery = true)
    List<Object[]> countByTag();

    Page<Post> findByUserSlackIdAndIsDeletedFalseOrderByCreatedAtDesc(String userSlackId, Pageable pageable);

    long countByUserSlackIdAndIsDeletedFalse(String userSlackId);

    @Query(
            value = "SELECT COALESCE(SUM(reaction_count), 0) FROM posts WHERE user_slack_id = :slackId AND is_deleted = false",
            nativeQuery = true)
    long sumReactionsByUser(@Param("slackId") String slackId);

    // 마이페이지 "반응한 글" - 내가 이모지를 남긴 게시글 목록/개수
    @Query(
            value =
                    "SELECT * FROM posts WHERE is_deleted = false AND :slackId = ANY(reacted_user_ids) ORDER BY created_at DESC",
            countQuery = "SELECT count(*) FROM posts WHERE is_deleted = false AND :slackId = ANY(reacted_user_ids)",
            nativeQuery = true)
    Page<Post> findReactedByUser(@Param("slackId") String slackId, Pageable pageable);

    @Query(
            value = "SELECT count(*) FROM posts WHERE is_deleted = false AND :slackId = ANY(reacted_user_ids)",
            nativeQuery = true)
    long countReactedByUser(@Param("slackId") String slackId);

    // 홈 화면 순위보드 - 반응/댓글 많은 순 TOP3
    List<Post> findTop3ByIsDeletedFalseOrderByReactionCountDesc();

    List<Post> findTop3ByIsDeletedFalseOrderByReplyCountDesc();

    // 저장(북마크) 많이 된 순 TOP3 - [post_id, save_count] 배열 목록
    @Query(
            value = """
            SELECT p.id AS id, count(b.id) AS save_count FROM posts p
            JOIN bookmarks b ON b.post_id = p.id
            WHERE p.is_deleted = false
            GROUP BY p.id
            ORDER BY save_count DESC
            LIMIT 3
            """,
            nativeQuery = true)
    List<Object[]> findTopSaveCounts();

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
}
