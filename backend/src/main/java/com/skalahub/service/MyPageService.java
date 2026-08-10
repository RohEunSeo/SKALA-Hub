// 마이페이지 통계/내가 올린 글/저장한 글 조회
package com.skalahub.service;

import com.skalahub.dto.CategoryCountDto;
import com.skalahub.dto.MyPageCategoryCountsResponse;
import com.skalahub.dto.MyPageStatsResponse;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.entity.Post;
import com.skalahub.repository.BookmarkRepository;
import com.skalahub.repository.PostRepository;
import java.util.List;
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
    // category/tag는 값이 없으면(null) 필터 없이 전체 조회
    public PostPageResponse getMyPosts(String slackId, String tab, String category, String tag, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String safeCategory = blankToNull(category);
        String safeTag = blankToNull(tag);
        if ("saved".equals(tab)) {
            Page<Post> saved = bookmarkRepository
                    .findMySavedFiltered(slackId, safeCategory, safeTag, pageable)
                    .map(bookmark -> bookmark.getPost());
            return postService.wrapPage(saved);
        }
        if ("reacted".equals(tab)) {
            return postService.wrapPage(postRepository.findReactedByUser(slackId, safeCategory, safeTag, pageable));
        }
        return postService.wrapPage(postRepository.findMyPosts(slackId, safeCategory, safeTag, pageable));
    }

    // 마이페이지 카테고리 필터 탭의 "(N)" 표시용 - tab별로 카테고리/태그 개수가 다름
    public MyPageCategoryCountsResponse getCategoryCounts(String slackId, String tab) {
        List<Object[]> categoryRows;
        List<Object[]> tagRows;
        if ("saved".equals(tab)) {
            categoryRows = bookmarkRepository.countSavedByCategory(slackId);
            tagRows = bookmarkRepository.countSavedByTag(slackId);
        } else if ("reacted".equals(tab)) {
            categoryRows = postRepository.countReactedByCategory(slackId);
            tagRows = postRepository.countReactedByTag(slackId);
        } else {
            categoryRows = postRepository.countMyPostsByCategory(slackId);
            tagRows = postRepository.countMyPostsByTag(slackId);
        }
        return new MyPageCategoryCountsResponse(toCountDtos(categoryRows), toCountDtos(tagRows));
    }

    private List<CategoryCountDto> toCountDtos(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new CategoryCountDto((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
