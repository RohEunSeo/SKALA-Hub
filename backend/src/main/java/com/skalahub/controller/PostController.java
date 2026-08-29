// 피드 게시글 조회 API (SecurityConfig에서 로그인 필요로 설정됨)
package com.skalahub.controller;

import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.dto.PostTitleUpdateRequest;
import com.skalahub.dto.ReplyResponse;
import com.skalahub.service.PostService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/api/posts")
    public PostPageResponse getPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) Boolean hasLink,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return postService.search(category, tag, keyword, author, date, campus, hasLink, sort, safePage, safeSize);
    }

    @GetMapping("/api/posts/{id}")
    public PostResponse getPost(@PathVariable Long id) {
        return postService.getPost(id);
    }

    @GetMapping("/api/posts/{id}/replies")
    public List<ReplyResponse> getReplies(@PathVariable Long id) {
        return postService.getReplies(id);
    }

    // 게시글 작성자 본인 또는 관리자만 AI 제목 수정 가능 (권한 체크는 서비스에서)
    @PatchMapping("/api/posts/{id}/ai-title")
    public PostResponse updateAiTitle(
            @PathVariable Long id, @RequestBody PostTitleUpdateRequest request, Authentication authentication) {
        return postService.updateAiTitle(id, request.aiTitle(), authentication);
    }
}
