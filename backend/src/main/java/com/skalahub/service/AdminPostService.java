// 관리자 게시글 관리 - 카테고리/태그/핀 수동 수정, 미분류 게시글 조회/일괄 분류
package com.skalahub.service;

import com.skalahub.dto.AdminPostUpdateRequest;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.entity.Post;
import com.skalahub.repository.PostRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPostService {

    private final PostRepository postRepository;
    private final CategoryClassifier categoryClassifier;
    private final PostService postService;

    public AdminPostService(
            PostRepository postRepository, CategoryClassifier categoryClassifier, PostService postService) {
        this.postRepository = postRepository;
        this.categoryClassifier = categoryClassifier;
        this.postService = postService;
    }

    public PostPageResponse getUncategorized(int page, int size) {
        return postService.wrapPage(postRepository.findUncategorized(PageRequest.of(page, size)));
    }

    @Transactional
    public PostResponse updatePost(Long id, AdminPostUpdateRequest request) {
        Post post = postRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"));

        if (request.category() != null) {
            post.setCategory(request.category());
        }
        if (request.tags() != null) {
            post.setTags(request.tags());
        }
        if (request.isPinned() != null) {
            post.setIsPinned(request.isPinned());
        }
        post = postRepository.save(post);
        return postService.toResponse(post);
    }

    // 미분류 게시글 전체를 Claude로 재분류 - 관리자가 수동으로 트리거
    @Transactional
    public int classifyAllUncategorized() {
        List<Post> uncategorized = postRepository.findAllUncategorized();
        for (Post post : uncategorized) {
            categoryClassifier.classify(post);
            postRepository.save(post);
        }
        return uncategorized.size();
    }
}
