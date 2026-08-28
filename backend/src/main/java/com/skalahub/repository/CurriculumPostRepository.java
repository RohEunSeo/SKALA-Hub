package com.skalahub.repository;

import com.skalahub.entity.CurriculumPost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CurriculumPostRepository extends JpaRepository<CurriculumPost, Long> {

    Optional<CurriculumPost> findByPost_Id(Long postId);

    List<CurriculumPost> findByStageAndIsExcludedFalseAndPost_IsDeletedFalseOrderByPost_CreatedAtDesc(String stage);

    List<CurriculumPost> findByStageAndSubCategoryAndIsExcludedFalseAndPost_IsDeletedFalseOrderByPost_CreatedAtDesc(
            String stage, String subCategory);

    List<CurriculumPost> findByPost_IdInAndIsExcludedFalse(List<Long> postIds);

    // 다이어그램 카드에 표시할 단계별 게시글 수 (제외/삭제된 건 제외)
    @Query(
            value =
                    """
            SELECT cp.stage AS stage, count(*) AS cnt FROM curriculum_posts cp
            JOIN posts p ON p.id = cp.post_id
            WHERE cp.is_excluded = false AND p.is_deleted = false
            GROUP BY cp.stage
            """,
            nativeQuery = true)
    List<Object[]> countByStage();
}
