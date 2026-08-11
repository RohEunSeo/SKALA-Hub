// 관리자 전용 API (role=admin만 접근 - SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.AdminLinkUpdateRequest;
import com.skalahub.dto.AdminPostUpdateRequest;
import com.skalahub.dto.BotReplyResponse;
import com.skalahub.dto.BotReplyUpdateRequest;
import com.skalahub.dto.LinkGroupDto;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.entity.SyncFailure;
import com.skalahub.service.AdminLinkService;
import com.skalahub.service.AdminPostService;
import com.skalahub.service.LinkService;
import com.skalahub.service.SlackSyncService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SlackSyncService slackSyncService;
    private final AdminPostService adminPostService;
    private final AdminLinkService adminLinkService;
    private final LinkService linkService;

    public AdminController(
            SlackSyncService slackSyncService,
            AdminPostService adminPostService,
            AdminLinkService adminLinkService,
            LinkService linkService) {
        this.slackSyncService = slackSyncService;
        this.adminPostService = adminPostService;
        this.adminLinkService = adminLinkService;
        this.linkService = linkService;
    }

    // 최근 N일 동기화 (게시글/댓글 수집 + 미분류 게시글 카테고리 분류) - API 호출량이 적어 자주 눌러도 부담 없음
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        try {
            SlackSyncService.SyncSummary summary = slackSyncService.incrementalSync();
            return ResponseEntity.ok(toSyncResponse(summary));
        } catch (SlackSyncService.SyncAlreadyRunningException alreadyRunning) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", alreadyRunning.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    // 채널 히스토리 전체 재수집 - 평소엔 매일 새벽 자동 실행되므로 과거 데이터 복구 등 급할 때만 수동 실행
    @PostMapping("/sync-full")
    public ResponseEntity<Map<String, Object>> syncFull() {
        try {
            SlackSyncService.SyncSummary summary = slackSyncService.syncAll();
            return ResponseEntity.ok(toSyncResponse(summary));
        } catch (SlackSyncService.SyncAlreadyRunningException alreadyRunning) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", alreadyRunning.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toSyncResponse(SlackSyncService.SyncSummary summary) {
        return Map.of(
                "postsProcessed", summary.postsProcessed(),
                "newPosts", summary.newPosts(),
                "repliesProcessed", summary.repliesProcessed(),
                "durationMs", summary.durationMs());
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

    // 링크 모음 탭 카드 제목 수동 수정 / 숨김 처리 (URL 기준, 전역)
    @PatchMapping("/links")
    public void updateLink(@RequestBody AdminLinkUpdateRequest request) {
        adminLinkService.updateLink(request);
    }

    // 숨긴 링크를 평소와 동일한 카드 형태로 - 링크 모음 탭 "정렬" 드롭다운의 관리자 전용 "숨김" 옵션에서 사용
    @GetMapping("/links/hidden")
    public List<LinkGroupDto> getHiddenLinks() {
        return linkService.getHiddenLinkGroups();
    }

    // 슬랙 재수집 없이 - 이미 DB에 있는 게시글 본문에서 아직 미리보기 캐시가 없는 링크만 다시 fetch 시도
    @PostMapping("/links/backfill")
    public Map<String, Object> backfillLinkPreviews() {
        int attempted = adminLinkService.backfillPreviews();
        return Map.of("attempted", attempted);
    }

    // 미분류 게시글 전체를 Claude로 일괄 재분류
    @PostMapping("/posts/classify-all")
    public Map<String, Object> classifyAll() {
        AdminPostService.ClassifyResult result = adminPostService.classifyAllUncategorized();
        return Map.of("classified", result.classified(), "failed", result.failed());
    }

    // 동기화 실패 목록 - 슬랙 채널에는 알리지 않고 여기서만 확인
    @GetMapping("/sync-failures")
    public List<SyncFailure> getSyncFailures() {
        return slackSyncService.getSyncFailures();
    }

    // 슬랙 봇이 남긴 동기화 안내 댓글 목록
    @GetMapping("/bot-replies")
    public List<BotReplyResponse> getBotReplies() {
        return adminPostService.getBotReplies();
    }

    // 슬랙 봇이 남긴 댓글 삭제 (ts로 식별) - 쿼리 파라미터로 받음: ts에 "."이 있어 경로변수로 쓰면 라우팅이 꼬일 수 있음
    @DeleteMapping("/bot-replies")
    public ResponseEntity<?> deleteBotReply(@RequestParam String ts) {
        try {
            adminPostService.deleteBotReply(ts);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "슬랙은 삭제됐지만 서버 반영 중 오류: " + e.getMessage()));
        }
    }

    // 슬랙 봇이 남긴 댓글 내용 수정 (ts + 새 내용)
    @PutMapping("/bot-replies")
    public ResponseEntity<?> updateBotReply(
            @RequestParam String ts, @RequestBody BotReplyUpdateRequest request) {
        try {
            adminPostService.updateBotReply(ts, request.content());
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "슬랙은 수정됐지만 서버 반영 중 오류: " + e.getMessage()));
        }
    }

    // 로컬 환경 동기화로 보류됐던 성공 알림을 배포 환경에서 지금 보냄
    @PostMapping("/pending-notifications/{postId}/send")
    public ResponseEntity<?> sendPendingNotification(@PathVariable Long postId) {
        try {
            adminPostService.sendPendingNotification(postId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }
}
