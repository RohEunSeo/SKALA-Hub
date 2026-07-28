// 슬랙 OAuth 로그인 처리 (인가 URL 생성, 코드 교환, 유저 정보 조회/파싱, DB 저장)
package com.skalahub.service;

import tools.jackson.databind.JsonNode;
import com.skalahub.entity.User;
import com.skalahub.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// "Sign in with Slack" OpenID Connect 플로우 사용 (구형 identity.* user_scope는 최신 앱에서 미지원)
@Service
public class SlackAuthService {

    private static final Logger log = LoggerFactory.getLogger(SlackAuthService.class);

    private final UserRepository userRepository;
    private final RestClient restClient = RestClient.create();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String userToken;

    private volatile String cachedTeamId;

    public SlackAuthService(
            UserRepository userRepository,
            @Value("${slack.client-id}") String clientId,
            @Value("${slack.client-secret}") String clientSecret,
            @Value("${slack.redirect-uri}") String redirectUri,
            @Value("${slack.user-token}") String userToken) {
        this.userRepository = userRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.userToken = userToken;
    }

    // 슬랙 로그인 인가 URL 생성 (team 파라미터로 워크스페이스 고정 → 워크스페이스 선택 단계 생략)
    public String buildAuthorizeUrl() {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode("openid profile email", StandardCharsets.UTF_8);
        String url = "https://slack.com/openid/connect/authorize"
                + "?response_type=code"
                + "&scope=" + encodedScope
                + "&client_id=" + clientId
                + "&redirect_uri=" + encodedRedirect
                + "&state=" + UUID.randomUUID()
                + "&nonce=" + UUID.randomUUID();

        String teamId = resolveTeamId();
        if (teamId != null) {
            url += "&team=" + teamId;
        }
        return url;
    }

    // SLACK_USER_TOKEN으로 워크스페이스 team_id 조회 (최초 1회 캐싱)
    private String resolveTeamId() {
        if (cachedTeamId != null) {
            return cachedTeamId;
        }
        try {
            JsonNode response = restClient.get()
                    .uri("https://slack.com/api/auth.test")
                    .header("Authorization", "Bearer " + userToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null && response.path("ok").asBoolean(false)) {
                cachedTeamId = response.path("team_id").asString(null);
            } else {
                log.warn("auth.test 실패 응답: {}", response);
            }
        } catch (Exception e) {
            // team 파라미터 없이도 로그인은 가능하므로 조회 실패는 무시
            log.warn("auth.test 호출 실패, team 파라미터 없이 진행", e);
        }
        return cachedTeamId;
    }

    // 콜백 code로 로그인 처리 후 저장된 유저 반환
    public User loginWithCode(String code) {
        String slackUserId = exchangeCodeForUserId(code);
        JsonNode slackUser = fetchUserProfile(slackUserId);
        return upsertUser(slackUserId, slackUser);
    }

    private String exchangeCodeForUserId(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        JsonNode tokenResponse = restClient.post()
                .uri("https://slack.com/api/openid.connect.token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (tokenResponse == null || !tokenResponse.path("ok").asBoolean(false)) {
            throw new IllegalStateException(
                    "슬랙 OAuth 코드 교환 실패: " + describeError(tokenResponse));
        }
        String accessToken = tokenResponse.path("access_token").asString();

        JsonNode userInfo = restClient.get()
                .uri("https://slack.com/api/openid.connect.userInfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        if (userInfo == null || !userInfo.path("ok").asBoolean(false)) {
            throw new IllegalStateException(
                    "슬랙 유저 식별 실패: " + describeError(userInfo));
        }
        return userInfo.path("sub").asString();
    }

    private JsonNode fetchUserProfile(String slackUserId) {
        // SLACK_USER_TOKEN(관리자 토큰)으로 display_name 등 상세 프로필 조회
        JsonNode response = restClient.get()
                .uri("https://slack.com/api/users.info?user={id}", slackUserId)
                .header("Authorization", "Bearer " + userToken)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("ok").asBoolean(false)) {
            throw new IllegalStateException(
                    "슬랙 유저 정보 조회 실패: " + describeError(response));
        }
        return response.path("user");
    }

    private User upsertUser(String slackUserId, JsonNode slackUser) {
        JsonNode profile = slackUser.path("profile");

        String displayName = profile.path("display_name").asString("");
        if (displayName.isBlank()) {
            displayName = profile.path("real_name")
                    .asString(slackUser.path("real_name").asString(slackUserId));
        }
        String imageUrl = profile.path("image_192").asString(null);

        // "4기_판교_5반_노은서" → cohort/campus/classNum/name 분리
        String[] parts = displayName.split("_");
        String cohort = null;
        String campus = null;
        String classNum = null;
        String name = displayName;
        if (parts.length == 4) {
            cohort = parts[0];
            campus = parts[1];
            classNum = parts[2];
            name = parts[3];
        }

        User user = userRepository.findById(slackUserId).orElseGet(User::new);
        boolean isNew = user.getSlackId() == null;

        user.setSlackId(slackUserId);
        user.setName(name);
        user.setCohort(cohort);
        user.setCampus(campus);
        user.setClassNum(classNum);
        user.setProfileImg(imageUrl);
        if (isNew) {
            user.setRole("student");
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setLastLogin(LocalDateTime.now());

        return userRepository.save(user);
    }

    private String describeError(JsonNode response) {
        return response != null ? response.path("error").asString("unknown") : "no response";
    }
}
