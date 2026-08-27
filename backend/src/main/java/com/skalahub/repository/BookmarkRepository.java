// Bookmark 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUser_SlackIdAndPost_Id(String slackId, Long postId);

    List<Bookmark> findByUser_SlackId(String slackId);

    long countByUser_SlackId(String slackId);

    void deleteByUser_SlackIdAndPost_Id(String slackId, Long postId);

    // 마이페이지 "저장한 글" - category/tag는 값이 없으면(null) 무시
    @Query(
            value = """
            SELECT b.* FROM bookmarks b
            JOIN posts p ON p.id = b.post_id
            WHERE b.user_id = :slackId
              AND p.is_deleted = false
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
            ORDER BY b.saved_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM bookmarks b
            JOIN posts p ON p.id = b.post_id
            WHERE b.user_id = :slackId
              AND p.is_deleted = false
              AND (CAST(:category AS varchar) IS NULL OR p.category = CAST(:category AS varchar))
              AND (CAST(:tag AS varchar) IS NULL OR CAST(:tag AS varchar) = ANY(p.tags))
            """,
            nativeQuery = true)
    Page<Bookmark> findMySavedFiltered(
            @Param("slackId") String slackId,
            @Param("category") String category,
            @Param("tag") String tag,
            Pageable pageable);

    // 마이페이지 카테고리 필터 탭의 "(N)" 표시용 - "저장한 글" 카테고리/태그별 개수
    @Query(
            value = """
            SELECT p.category, count(*) FROM bookmarks b
            JOIN posts p ON p.id = b.post_id
            WHERE b.user_id = :slackId AND p.category IS NOT NULL AND p.is_deleted = false
            GROUP BY p.category
            """,
            nativeQuery = true)
    List<Object[]> countSavedByCategory(@Param("slackId") String slackId);

    @Query(
            value = """
            SELECT tag, count(*) FROM bookmarks b
            JOIN posts p ON p.id = b.post_id, unnest(p.tags) AS tag
            WHERE b.user_id = :slackId AND p.is_deleted = false
            GROUP BY tag
            """,
            nativeQuery = true)
    List<Object[]> countSavedByTag(@Param("slackId") String slackId);
}
