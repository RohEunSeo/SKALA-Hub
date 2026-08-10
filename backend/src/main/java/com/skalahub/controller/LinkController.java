// 링크 모음 탭 조회 API - URL 기준으로 그룹핑된 링크 목록
package com.skalahub.controller;

import com.skalahub.dto.LinkGroupPageResponse;
import com.skalahub.service.LinkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/api/links")
    public LinkGroupPageResponse getLinks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String campus,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        // 그룹 목록은 필터에 맞는 게시글 전체를 매 요청마다 다시 훑어서 계산하므로, size를 작게 여러 페이지로
        // 쪼개면 스크롤할 때마다 이 재계산이 반복돼 느려짐 - 상한을 넉넉히 둬서 보통은 한 번에 다 받아가게 함
        int safeSize = Math.max(1, Math.min(size, 2000));
        return linkService.getLinkGroups(category, tag, keyword, author, date, campus, sort, safePage, safeSize);
    }
}
