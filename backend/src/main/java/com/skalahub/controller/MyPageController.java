// 마이페이지 API - 로그인 필요 (SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.MyPageStatsResponse;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.service.MyPageService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
public class MyPageController {

    private final MyPageService myPageService;

    public MyPageController(MyPageService myPageService) {
        this.myPageService = myPageService;
    }

    @GetMapping("/stats")
    public MyPageStatsResponse getStats(Principal principal) {
        return myPageService.getStats(principal.getName());
    }

    @GetMapping("/posts")
    public PostPageResponse getMyPosts(
            Principal principal,
            @RequestParam(defaultValue = "posts") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return myPageService.getMyPosts(principal.getName(), tab, safePage, safeSize);
    }
}
