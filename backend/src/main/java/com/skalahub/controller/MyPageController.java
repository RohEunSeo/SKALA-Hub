// 마이페이지 API - 로그인 필요 (SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.AccountLinkResponse;
import com.skalahub.dto.GoogleCodeRequest;
import com.skalahub.dto.MyPageCategoryCountsResponse;
import com.skalahub.dto.MyPageStatsResponse;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.service.GoogleAuthService;
import com.skalahub.service.MyPageService;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
public class MyPageController {

    private final MyPageService myPageService;
    private final GoogleAuthService googleAuthService;

    public MyPageController(MyPageService myPageService, GoogleAuthService googleAuthService) {
        this.myPageService = myPageService;
        this.googleAuthService = googleAuthService;
    }

    @GetMapping("/stats")
    public MyPageStatsResponse getStats(Principal principal) {
        return myPageService.getStats(principal.getName());
    }

    @GetMapping("/posts")
    public PostPageResponse getMyPosts(
            Principal principal,
            @RequestParam(defaultValue = "posts") String tab,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return myPageService.getMyPosts(principal.getName(), tab, category, tag, safePage, safeSize);
    }

    @GetMapping("/category-counts")
    public MyPageCategoryCountsResponse getCategoryCounts(
            Principal principal, @RequestParam(defaultValue = "posts") String tab) {
        return myPageService.getCategoryCounts(principal.getName(), tab);
    }

    // 계정 설정 섹션 - 구글 계정 연동 상태
    @GetMapping("/account")
    public AccountLinkResponse getAccount(Principal principal) {
        return googleAuthService.getLinkStatus(principal.getName());
    }

    // 구글 계정 연동 (팝업에서 받은 인가 코드로 처리)
    @PostMapping("/account/google-link")
    public ResponseEntity<?> linkGoogleAccount(Principal principal, @RequestBody GoogleCodeRequest request) {
        try {
            return ResponseEntity.ok(googleAuthService.linkAccount(principal.getName(), request.code()));
        } catch (GoogleAuthService.GoogleAlreadyLinkedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "google_already_linked"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "google_link_failed"));
        }
    }

    // 구글 계정 연동 해제 (본인)
    @DeleteMapping("/account/google-link")
    public AccountLinkResponse unlinkGoogleAccount(Principal principal) {
        googleAuthService.unlinkAccount(principal.getName());
        return new AccountLinkResponse(false, null);
    }
}
