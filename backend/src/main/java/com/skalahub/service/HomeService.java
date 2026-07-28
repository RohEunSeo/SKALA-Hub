// 홈 화면 요약 (오늘 새 글 수 / 마지막 동기화 / 기수 D일째 / 카테고리별 글 수)
package com.skalahub.service;

import com.skalahub.dto.CategoryCountDto;
import com.skalahub.dto.HomeLeaderboardResponse;
import com.skalahub.dto.HomeSummaryResponse;
import com.skalahub.dto.LeaderboardEntryDto;
import com.skalahub.repository.PostRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final PostService postService;
    private final LocalDate cohortStartDate;

    public HomeService(
            PostRepository postRepository,
            PostService postService,
            @Value("${app.cohort-start-date}") String cohortStartDate) {
        this.postRepository = postRepository;
        this.postService = postService;
        this.cohortStartDate = LocalDate.parse(cohortStartDate);
    }

    public HomeSummaryResponse getSummary() {
        LocalDate today = LocalDate.now(ZONE);
        long todayNewPostCount = postRepository.countCreatedSince(today.atStartOfDay());
        long cohortDay = ChronoUnit.DAYS.between(cohortStartDate, today) + 1;

        List<CategoryCountDto> categoryCounts = postRepository.countByCategory().stream()
                .map(row -> new CategoryCountDto((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new HomeSummaryResponse(
                postRepository.countByIsDeletedFalse(),
                todayNewPostCount,
                postRepository.findLastSyncedAt(),
                Math.max(cohortDay, 0),
                categoryCounts);
    }

    public HomeLeaderboardResponse getLeaderboard() {
        List<LeaderboardEntryDto> topReactions = postRepository.findTop3ByIsDeletedFalseOrderByReactionCountDesc()
                .stream()
                .map(post -> new LeaderboardEntryDto(postService.toResponse(post), post.getReactionCount()))
                .toList();

        List<LeaderboardEntryDto> topComments = postRepository.findTop3ByIsDeletedFalseOrderByReplyCountDesc()
                .stream()
                .map(post -> new LeaderboardEntryDto(postService.toResponse(post), post.getReplyCount()))
                .toList();

        List<LeaderboardEntryDto> topSaves = new ArrayList<>();
        for (Object[] row : postRepository.findTopSaveCounts()) {
            Long postId = ((Number) row[0]).longValue();
            long saveCount = ((Number) row[1]).longValue();
            postRepository.findById(postId).ifPresent(post -> {
                if (Boolean.TRUE.equals(post.getIsDeleted())) {
                    return;
                }
                topSaves.add(new LeaderboardEntryDto(postService.toResponse(post), saveCount));
            });
        }

        return new HomeLeaderboardResponse(topReactions, topComments, topSaves);
    }
}
