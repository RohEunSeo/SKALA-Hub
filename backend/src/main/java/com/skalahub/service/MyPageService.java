// 마이페이지 통계/내가 올린 글/저장한 글 조회
package com.skalahub.service;

import com.skalahub.dto.MyPageStatsResponse;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.entity.Post;
import com.skalahub.repository.BookmarkRepository;
import com.skalahub.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MyPageService {

    private final PostRepository postRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PostService postService;

    public MyPageService(
            PostRepository postRepository, BookmarkRepository bookmarkRepository, PostService postService) {
        this.postRepository = postRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.postService = postService;
    }

    public MyPageStatsResponse getStats(String slackId) {
        long postCount = postRepository.countByUserSlackIdAndIsDeletedFalse(slackId);
        long reactionsReceived = postRepository.sumReactionsByUser(slackId);
        long savedCount = bookmarkRepository.countByUser_SlackId(slackId);
        long reactedCount = postRepository.countReactedByUser(slackId);
        return new MyPageStatsResponse(postCount, reactionsReceived, savedCount, reactedCount);
    }

    // tab: "posts"(내가 올린 글) / "saved"(저장한 글) / "reacted"(반응한 글)
    public PostPageResponse getMyPosts(String slackId, String tab, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if ("saved".equals(tab)) {
            Page<Post> saved = bookmarkRepository
                    .findByUser_SlackIdOrderBySavedAtDesc(slackId, pageable)
                    .map(bookmark -> bookmark.getPost());
            return postService.wrapPage(saved);
        }
        if ("reacted".equals(tab)) {
            return postService.wrapPage(postRepository.findReactedByUser(slackId, pageable));
        }
        Page<Post> myPosts = postRepository.findByUserSlackIdAndIsDeletedFalseOrderByCreatedAtDesc(slackId, pageable);
        return postService.wrapPage(myPosts);
    }
}
