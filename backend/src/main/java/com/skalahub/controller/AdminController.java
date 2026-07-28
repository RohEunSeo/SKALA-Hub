// 관리자 전용 API (role=admin만 접근 - SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.AdminPostUpdateRequest;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.service.AdminPostService;
import com.skalahub.service.SlackSyncService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SlackSyncService slackSyncService;
    private final AdminPostService adminPostService;

    public AdminController(SlackSyncService slackSyncService, AdminPostService adminPostService) {
        this.slackSyncService = slackSyncService;
        this.adminPostService = adminPostService;
    }

    // 슬랙 채널 전체 동기화 (게시글/댓글 수집 + 미분류 게시글 카테고리 분류)
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        try {
            SlackSyncService.SyncSummary summary = slackSyncService.syncAll();
            return ResponseEntity.ok(Map.of(
                    "postsProcessed", summary.postsProcessed(),
                    "newPosts", summary.newPosts(),
                    "repliesProcessed", summary.repliesProcessed(),
                    "durationMs", summary.durationMs()));
        } catch (IllegalStateException alreadyRunning) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", alreadyRunning.getMessage()));
        }
    }

    // 미분류(카테고리 없음) 게시글 목록
    @GetMapping("/posts/uncategorized")
    public PostPageResponse getUncategorized(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminPostService.getUncategorized(page, Math.max(1, Math.min(size, 100)));
    }

    // 게시글 카테고리/태그/핀 고정 수동 수정
    @PatchMapping("/posts/{id}")
    public PostResponse updatePost(@PathVariable Long id, @RequestBody AdminPostUpdateRequest request) {
        return adminPostService.updatePost(id, request);
    }

    // 미분류 게시글 전체를 Claude로 일괄 재분류
    @PostMapping("/posts/classify-all")
    public Map<String, Object> classifyAll() {
        int classified = adminPostService.classifyAllUncategorized();
        return Map.of("classified", classified);
    }
}
