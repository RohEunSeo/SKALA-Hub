// 슬랙 OAuth 로그인 엔드포인트
package com.skalahub.controller;

import com.skalahub.entity.User;
import com.skalahub.service.JwtService;
import com.skalahub.service.SlackAuthService;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SlackAuthService slackAuthService;
    private final JwtService jwtService;
    private final String frontendUrl;

    public AuthController(
            SlackAuthService slackAuthService,
            JwtService jwtService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.slackAuthService = slackAuthService;
        this.jwtService = jwtService;
        this.frontendUrl = frontendUrl;
    }

    // 슬랙 OAuth 인가 화면으로 리다이렉트
    @GetMapping("/slack")
    public ResponseEntity<Void> redirectToSlack() {
        return redirectTo(slackAuthService.buildAuthorizeUrl());
    }

    // 슬랙 인가 완료 후 콜백: 유저 저장 + JWT 발급 후 프론트로 리다이렉트
    @GetMapping("/slack/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        if (error != null || code == null) {
            return redirectTo(frontendUrl + "/?error=slack_oauth_failed");
        }
        try {
            User user = slackAuthService.loginWithCode(code);
            String token = jwtService.generateToken(user);
            return redirectTo(frontendUrl + "/?token=" + token);
        } catch (Exception e) {
            log.error("슬랙 로그인 실패", e);
            return redirectTo(frontendUrl + "/?error=slack_oauth_failed");
        }
    }

    private ResponseEntity<Void> redirectTo(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
