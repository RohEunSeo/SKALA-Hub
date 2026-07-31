// 슬랙 동기화 결과를 원 게시글 스레드에 봇 댓글로 안내 (추후 주간 인기글 요약 등 다른 봇 발신 기능도 여기에 추가)
package com.skalahub.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class SlackBotReplyService {

    private static final Logger log = LoggerFactory.getLogger(SlackBotReplyService.class);

    private final RestClient restClient = RestClient.create();

    private final String botToken;
    private final String channelId;
    private final String frontendUrl;
    private final boolean testMode;

    // 봇 자신의 슬랙 user_id - 한 번 조회하면 안 바뀌므로 캐싱 (동기화 중 봇 댓글을 걸러내는 데 사용)
    private volatile String cachedBotUserId;

    public SlackBotReplyService(
            @Value("${slack.bot-token:}") String botToken,
            @Value("${slack.channel-id}") String channelId,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${test-mode:false}") boolean testMode) {
        this.botToken = botToken;
        this.channelId = channelId;
        // FRONTEND_URL 환경변수 끝에 "/"가 붙어있어도(예: https://x.com/) 링크가 "//posts/.."로
        // 겹치지 않도록 방어
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.testMode = testMode;
    }

    public void notifySyncSuccess(String threadTs, Long postId) {
        String timestamp = formatTimestamp(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        String message = "✅ SKALA-Hub에 동기화되었습니다! (" + timestamp + ")\n"
                + "🔗 바로가기: " + frontendUrl + "/posts/" + postId;
        postThreadReply(threadTs, message);
    }

    public void notifySyncFailure(String threadTs) {
        postThreadReply(threadTs, "⚠️ SKALA-Hub 동기화에 실패했습니다. 관리자에게 문의해주세요!");
    }

    // 봇이 스레드에 남긴 댓글을 실제 학생 댓글과 구분하기 위한 봇 자신의 user_id 조회 (SlackSyncService가 사용)
    // 조회 실패 시 null - 호출부는 null이면 걸러내지 않고 그냥 넘어가야 함
    public String resolveBotUserId() {
        if (testMode || botToken.isBlank()) {
            return null;
        }
        String cached = cachedBotUserId;
        if (cached != null) {
            return cached;
        }
        try {
            JsonNode response = restClient.post()
                    .uri("https://slack.com/api/auth.test")
                    .header("Authorization", "Bearer " + botToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response.path("ok").asBoolean(false)) {
                cachedBotUserId = response.path("user_id").asString(null);
                return cachedBotUserId;
            }
            log.warn("봇 user_id 조회 실패: {}", describeError(response));
        } catch (Exception e) {
            log.warn("봇 user_id 조회 실패", e);
        }
        return null;
    }

    // 관리자가 봇 댓글을 직접 지울 때 사용 (AdminController에서 호출) - 실패하면 예외를 그대로 던져서 API 응답에 반영
    public void deleteReply(String ts) {
        if (testMode) {
            log.info("[TEST_MODE] 슬랙 댓글 삭제 스킵 - ts={}", ts);
            return;
        }
        JsonNode response = restClient.post()
                .uri("https://slack.com/api/chat.delete")
                .header("Authorization", "Bearer " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", channelId, "ts", ts))
                .retrieve()
                .body(JsonNode.class);
        if (!response.path("ok").asBoolean(false)) {
            throw new IllegalStateException("슬랙 댓글 삭제 실패: " + describeError(response));
        }
    }

    // 관리자가 봇 댓글 내용을 직접 고칠 때 사용 (AdminController에서 호출) - 실패하면 예외를 그대로 던져서 API 응답에 반영
    public void updateReply(String ts, String text) {
        if (testMode) {
            log.info("[TEST_MODE] 슬랙 댓글 수정 스킵 - ts={}, text={}", ts, text);
            return;
        }
        JsonNode response = restClient.post()
                .uri("https://slack.com/api/chat.update")
                .header("Authorization", "Bearer " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", channelId, "ts", ts, "text", text))
                .retrieve()
                .body(JsonNode.class);
        if (!response.path("ok").asBoolean(false)) {
            throw new IllegalStateException("슬랙 댓글 수정 실패: " + describeError(response));
        }
    }

    // 댓글 전송 실패가 동기화 스케줄러 자체를 멈추면 안 되므로 어떤 예외도 밖으로 던지지 않음 (동기화 흐름 전용)
    private void postThreadReply(String threadTs, String text) {
        if (testMode) {
            log.info("[TEST_MODE] 슬랙 댓글 전송 스킵 - threadTs={}, text={}", threadTs, text);
            return;
        }
        try {
            JsonNode response = restClient.post()
                    .uri("https://slack.com/api/chat.postMessage")
                    .header("Authorization", "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "channel", channelId,
                            "thread_ts", threadTs,
                            "text", text,
                            "unfurl_links", false,
                            "unfurl_media", false))
                    .retrieve()
                    .body(JsonNode.class);
            if (!response.path("ok").asBoolean(false)) {
                log.error("슬랙 스레드 댓글 전송 실패 (threadTs={}): {}", threadTs, describeError(response));
            }
        } catch (Exception e) {
            log.error("슬랙 스레드 댓글 전송 실패 (threadTs={})", threadTs, e);
        }
    }

    private String describeError(JsonNode response) {
        return response != null ? response.path("error").asString("unknown") : "no response";
    }

    // "07.31 12:10pm" 형식 - 슬랙 댓글에 표시할 동기화 완료 시각
    private String formatTimestamp(LocalDateTime time) {
        int hour12 = time.getHour() % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String ampm = time.getHour() < 12 ? "am" : "pm";
        return String.format("%02d.%02d %d:%02d%s",
                time.getMonthValue(), time.getDayOfMonth(), hour12, time.getMinute(), ampm);
    }
}
