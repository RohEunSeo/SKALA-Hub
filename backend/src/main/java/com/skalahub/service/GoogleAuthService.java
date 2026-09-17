// 구글 계정 연동/로그인 처리 (인가 코드 교환, 유저 정보 조회, 계정 매핑 저장)
// 교육 종료 후 슬랙 계정이 비활성화돼도 로그인을 유지하기 위한 대체 인증 수단.
// slack_id를 PK로 그대로 두고, 같은 유저 행에 google_id/google_email만 매핑한다 (식별 체계 변경 없음).
package com.skalahub.service;

import com.skalahub.dto.AccountLinkResponse;
import com.skalahub.dto.GoogleLinkPageResponse;
import com.skalahub.dto.GoogleLinkResponse;
import com.skalahub.entity.User;
import com.skalahub.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private final UserRepository userRepository;
    private final RestClient restClient = RestClient.create();

    private final String clientId;
    private final String clientSecret;

    public GoogleAuthService(
            UserRepository userRepository,
            @Value("${google.client-id}") String clientId,
            @Value("${google.client-secret}") String clientSecret) {
        this.userRepository = userRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // 구글 로그인 (연동된 계정만 가능) - 없으면 GoogleNotLinkedException
    @Transactional
    public User loginWithCode(String code) {
        GoogleUser googleUser = exchangeCode(code);
        User user = userRepository
                .findByGoogleId(googleUser.sub())
                .orElseThrow(GoogleNotLinkedException::new);
        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }

    // 이미 Slack으로 로그인된 유저가 마이페이지에서 구글 계정을 연동
    @Transactional
    public AccountLinkResponse linkAccount(String slackId, String code) {
        GoogleUser googleUser = exchangeCode(code);
        Optional<User> existing = userRepository.findByGoogleId(googleUser.sub());
        if (existing.isPresent() && !existing.get().getSlackId().equals(slackId)) {
            throw new GoogleAlreadyLinkedException();
        }

        User user = userRepository
                .findById(slackId)
                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다: " + slackId));
        user.setGoogleId(googleUser.sub());
        user.setGoogleEmail(googleUser.email());

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 동시성 경합으로 방금 다른 요청이 먼저 연동한 경우
            throw new GoogleAlreadyLinkedException();
        }
        return new AccountLinkResponse(true, saved.getGoogleEmail());
    }

    // 본인 마이페이지 해제 / 관리자 강제 해제 공용
    @Transactional
    public void unlinkAccount(String slackId) {
        User user = userRepository
                .findById(slackId)
                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다: " + slackId));
        user.setGoogleId(null);
        user.setGoogleEmail(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AccountLinkResponse getLinkStatus(String slackId) {
        User user = userRepository
                .findById(slackId)
                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다: " + slackId));
        return new AccountLinkResponse(user.getGoogleId() != null, user.getGoogleEmail());
    }

    // 관리자 "구글 계정 연동 관리" 화면 - status: all/linked/unlinked
    @Transactional(readOnly = true)
    public GoogleLinkPageResponse getLinkStatusPage(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name"));
        Page<User> result =
                switch (status) {
                    case "linked" -> userRepository.findByGoogleIdIsNotNull(pageable);
                    case "unlinked" -> userRepository.findByGoogleIdIsNull(pageable);
                    default -> userRepository.findAll(pageable);
                };

        List<GoogleLinkResponse> content = result.getContent().stream()
                .map(u -> new GoogleLinkResponse(
                        u.getSlackId(),
                        u.getName(),
                        u.getCohort(),
                        u.getCampus(),
                        u.getClassNum(),
                        u.getGoogleId() != null,
                        u.getGoogleEmail()))
                .toList();
        return new GoogleLinkPageResponse(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    // 팝업 코드플로우 전용: redirect_uri=postmessage (실제 URI 아닌 구글 예약값)
    private GoogleUser exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", "postmessage");

        JsonNode tokenResponse;
        try {
            tokenResponse = restClient
                    .post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.warn("구글 토큰 교환 실패", e);
            throw new GoogleOAuthFailedException();
        }
        if (tokenResponse == null || tokenResponse.path("access_token").isMissingNode()) {
            log.warn("구글 토큰 교환 실패 응답: {}", tokenResponse);
            throw new GoogleOAuthFailedException();
        }
        String accessToken = tokenResponse.path("access_token").asString();

        JsonNode userInfo;
        try {
            userInfo = restClient
                    .get()
                    .uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.warn("구글 유저 정보 조회 실패", e);
            throw new GoogleOAuthFailedException();
        }
        if (userInfo == null || userInfo.path("sub").isMissingNode()) {
            log.warn("구글 유저 정보 조회 실패 응답: {}", userInfo);
            throw new GoogleOAuthFailedException();
        }
        return new GoogleUser(userInfo.path("sub").asString(), userInfo.path("email").asString(null));
    }

    private record GoogleUser(String sub, String email) {
    }

    public static class GoogleNotLinkedException extends RuntimeException {
    }

    public static class GoogleAlreadyLinkedException extends RuntimeException {
    }

    public static class GoogleOAuthFailedException extends RuntimeException {
    }
}
