// SKALA 커리큘럼 탭 공개 조회 - 단계별 게시글 목록/카운트 (로그인한 교육생 누구나 접근)
package com.skalahub.service;

import com.skalahub.dto.CurriculumPostResponse;
import com.skalahub.entity.CurriculumPost;
import com.skalahub.repository.CurriculumPostRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurriculumService {

    private final CurriculumPostRepository curriculumPostRepository;
    private final PostService postService;

    public CurriculumService(CurriculumPostRepository curriculumPostRepository, PostService postService) {
        this.curriculumPostRepository = curriculumPostRepository;
        this.postService = postService;
    }

    @Transactional(readOnly = true)
    public List<CurriculumPostResponse> getPostsByStage(String stage, String subCategory) {
        List<CurriculumPost> entries = (subCategory == null || subCategory.isBlank())
                ? curriculumPostRepository.findByStageAndIsExcludedFalseAndPost_IsDeletedFalseOrderByPost_CreatedAtDesc(
                        stage)
                : curriculumPostRepository
                        .findByStageAndSubCategoryAndIsExcludedFalseAndPost_IsDeletedFalseOrderByPost_CreatedAtDesc(
                                stage, subCategory);
        return entries.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : curriculumPostRepository.countByStage()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    // 하위 카테고리 필터 pill에 표시할 개수 - stage -> (subCategory -> count)
    @Transactional(readOnly = true)
    public Map<String, Map<String, Long>> getSubCategoryCounts() {
        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        for (Object[] row : curriculumPostRepository.countByStageAndSubCategory()) {
            String stage = (String) row[0];
            String subCategory = (String) row[1];
            long count = ((Number) row[2]).longValue();
            counts.computeIfAbsent(stage, key -> new LinkedHashMap<>()).put(subCategory, count);
        }
        return counts;
    }

    private CurriculumPostResponse toResponse(CurriculumPost entry) {
        return new CurriculumPostResponse(
                postService.toResponse(entry.getPost()),
                entry.getStage(),
                entry.getSubCategory(),
                entry.getAddedBy(),
                entry.getCreatedAt());
    }
}
