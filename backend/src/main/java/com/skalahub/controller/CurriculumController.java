// SKALA 커리큘럼 탭 - 단계별 게시글 조회 (로그인한 교육생 누구나 접근)
package com.skalahub.controller;

import com.skalahub.dto.CurriculumPostResponse;
import com.skalahub.service.CurriculumService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curriculum")
public class CurriculumController {

    private final CurriculumService curriculumService;

    public CurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping("/posts")
    public List<CurriculumPostResponse> getPosts(
            @RequestParam String stage, @RequestParam(required = false) String subCategory) {
        return curriculumService.getPostsByStage(stage, subCategory);
    }

    // 다이어그램 카드에 표시할 단계별 게시글 수
    @GetMapping("/counts")
    public Map<String, Long> getCounts() {
        return curriculumService.getCounts();
    }

    // 하위 카테고리 필터 pill에 표시할 단계별 하위 카테고리 게시글 수
    @GetMapping("/counts/subcategory")
    public Map<String, Map<String, Long>> getSubCategoryCounts() {
        return curriculumService.getSubCategoryCounts();
    }
}
