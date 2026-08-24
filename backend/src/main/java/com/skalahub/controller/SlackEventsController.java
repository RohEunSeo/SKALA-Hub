// 슬랙 Events API 웹훅 수신 - 정보공유채널에 새 글이 올라오는 즉시 관리자에게 개인 DM을 보내기 위한 실시간 트리거.
// 인증(JWT) 없이 슬랙 서버가 직접 호출하므로 서명 검증으로 위조 요청을 막는다 (SecurityConfig의 permitAll 대상).
package com.skalahub.controller;

import com.skalahub.service.SlackDmNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class SlackEventsController {

    private static final Logger log = LoggerFactory.getLogger(SlackEventsController.class);

    // 시스템 알림 메시지는 새 글로 취급하지 않음 - SlackSyncService.SKIP_SUBTYPES와 동일한 목적
    private static final Set<String> SKIP_SUBTYPES = Set.of(
            "channel_join", "channel_leave", "channel_topic", "channel_purpose",
            "channel_name", "channel_archive", "channel_unarchive",
            "bot_add", "bot_remove", "pinned_item", "unpinned_item");

    private static final long MAX_TIMESTAMP_SKEW_SECONDS = 60 * 5;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SlackDmNotificationService slackDmNotificationService;
    private final String signingSecret;
    private final String channelId;

    public SlackEventsController(
            SlackDmNotificationService slackDmNotificationService,
            @Value("${slack.signing-secret:}") String signingSecret,
            @Value("${slack.channel-id}") String channelId) {
        this.slackDmNotificationService = slackDmNotificationService;
        this.signingSecret = signingSecret;
        this.channelId = channelId;
    }

    @PostMapping(value = "/api/slack/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleEvent(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Slack-Signature", required = false) String signature,
            @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Slack-Retry-Num", required = false) String retryNum,
            HttpServletRequest request) {

        if (!isValidSignature(signature, timestamp, rawBody)) {
            log.warn("슬랙 이벤트 서명 검증 실패 (ip={})", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode payload;
        try {
            payload = jsonMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("슬랙 이벤트 파싱 실패", e);
            return ResponseEntity.ok().build();
        }

        String type = payload.path("type").asString("");
        if ("url_verification".equals(type)) {
            return ResponseEntity.ok(Map.of("challenge", payload.path("challenge").asString("")));
        }

        // 재전송된 배달은 무시 (중복 DM 방지) - Slack은 3초 안에 200을 못 받으면 최대 3번까지 재시도함
        if (retryNum != null && !retryNum.isBlank()) {
            return ResponseEntity.ok().build();
        }

        if ("event_callback".equals(type)) {
            handleEventCallback(payload.path("event"));
        }
        return ResponseEntity.ok().build();
    }

    private void handleEventCallback(JsonNode event) {
        if (!"message".equals(event.path("type").asString(""))) {
            return;
        }
        if (!channelId.equals(event.path("channel").asString(""))) {
            return;
        }
        String subtype = event.path("subtype").asString("");
        if (!subtype.isBlank() && SKIP_SUBTYPES.contains(subtype)) {
            return;
        }
        if (!event.path("bot_id").isMissingNode()) {
            return;
        }
        String ts = event.path("ts").asString("");
        String threadTs = event.path("thread_ts").asString("");
        if (!threadTs.isBlank() && !threadTs.equals(ts)) {
            // 새 글이 아니라 스레드 답글
            return;
        }
        String userId = event.path("user").asString(null);
        if (userId == null || ts.isBlank()) {
            return;
        }
        slackDmNotificationService.sendNewPostAlert(userId, event.path("text").asString(""), ts, channelId);
    }

    private boolean isValidSignature(String signature, String timestamp, String rawBody) {
        if (signingSecret.isBlank() || signature == null || timestamp == null) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - ts) > MAX_TIMESTAMP_SKEW_SECONDS) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        String baseString = "v0:" + timestamp + ":" + rawBody;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            String computed = "v0=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("슬랙 서명 검증 중 오류", e);
            return false;
        }
    }
}
