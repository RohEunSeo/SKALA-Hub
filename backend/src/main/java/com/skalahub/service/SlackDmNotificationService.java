// 관리자 본인에게만 보내는 슬랙 개인 DM 알림 (새 글 감지 즉시 알림 + 동기화/분류 결과 알림)
package com.skalahub.service;

import com.skalahub.entity.Post;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class SlackDmNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SlackDmNotificationService.class);

    private final RestClient restClient = RestClient.create();

    private final String botToken;
    private final String adminDmUserId;
    private final String workspaceUrl;
    private final String frontendUrl;
    private final boolean testMode;

    public SlackDmNotificationService(
            @Value("${slack.bot-token:}") String botToken,
            @Value("${slack.admin-dm-user-id:}") String adminDmUserId,
            @Value("${slack.workspace-url}") String workspaceUrl,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${test-mode:false}") boolean testMode) {
        this.botToken = botToken;
        this.adminDmUserId = adminDmUserId;
        this.workspaceUrl = workspaceUrl.endsWith("/") ? workspaceUrl.substring(0, workspaceUrl.length() - 1) : workspaceUrl;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.testMode = testMode;
    }

    // Slack Events API 웹훅에서 새 글을 감지한 즉시 호출 - 컨트롤러가 3초 안에 응답해야 하므로 비동기로 분리
    @Async
    public void sendNewPostAlert(String userSlackId, String textPreview, String slackTs, String channelId) {
        String deepLink = workspaceUrl + "/archives/" + channelId + "/p" + slackTs.replace(".", "");
        String preview = textPreview == null || textPreview.isBlank() ? "(내용 없음)" : truncate(textPreview, 200);
        String message = "🆕 새 글이 올라왔어요!\n"
                + "작성자: <@" + userSlackId + ">\n"
                + "\"" + preview + "\"\n"
                + "🔗 슬랙에서 보기: " + deepLink;
        sendDm(message);
    }

    // 동기화(글 저장) 성공 시 호출 - 분류(category) 성공 여부에 따라 메시지 분기
    public void sendSyncResult(Post post, long totalPostCount) {
        String category = post.getCategory();
        List<String> tags = post.getTags();
        String statusLine;
        if (category == null || category.isBlank()) {
            statusLine = "⚠️ 동기화는 성공했지만 분류에 실패했어요 (다음 동기화 때 재시도됩니다)";
        } else {
            String tagPart = (tags == null || tags.isEmpty()) ? "" : " " + tags;
            statusLine = "✅ 동기화 및 분류 완료 - 카테고리: " + category + tagPart;
        }
        String message = statusLine + "\n"
                + "전체 " + totalPostCount + "번째 글\n"
                + "🔗 바로가기: " + frontendUrl + "/posts/" + post.getId();
        sendDm(message);
    }

    // 게시글 저장 자체가 실패한 경우 호출 (분류 실패와는 다름 - 이건 진짜 동기화 실패)
    public void sendSyncFailure(String slackTs, String preview, String errorMessage, long totalPostCount) {
        String message = "❌ 동기화 실패!\n"
                + "전체 " + totalPostCount + "번째 글 근처\n"
                + "내용: \"" + truncate(preview == null ? "" : preview, 200) + "\"\n"
                + "오류: " + errorMessage;
        sendDm(message);
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // 어떤 예외도 밖으로 던지지 않음 - 동기화 흐름이나 웹훅 응답을 절대 막으면 안 됨
    private void sendDm(String text) {
        if (adminDmUserId == null || adminDmUserId.isBlank()) {
            log.warn("SLACK_ADMIN_DM_USER_ID 미설정 - DM 전송 스킵");
            return;
        }
        if (testMode) {
            log.info("[TEST_MODE] 슬랙 관리자 DM 전송 스킵 - text={}", text);
            return;
        }
        try {
            JsonNode response = postMessageWithRetry(text);
            if (!response.path("ok").asBoolean(false)) {
                log.error("슬랙 관리자 DM 전송 실패: {}", describeError(response));
            }
        } catch (Exception e) {
            log.error("슬랙 관리자 DM 전송 실패", e);
        }
    }

    private JsonNode postMessageWithRetry(String text) {
        try {
            return postMessage(text);
        } catch (HttpClientErrorException.TooManyRequests e) {
            long waitSeconds = 1;
            String retryAfter = e.getResponseHeaders() != null
                    ? e.getResponseHeaders().getFirst("Retry-After")
                    : null;
            if (retryAfter != null) {
                try {
                    waitSeconds = Long.parseLong(retryAfter);
                } catch (NumberFormatException ignored) {
                    // 기본 1초 대기 유지
                }
            }
            log.warn("슬랙 레이트리밋 - {}초 대기 후 DM 재전송", waitSeconds);
            try {
                Thread.sleep(waitSeconds * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return postMessage(text);
        }
    }

    private JsonNode postMessage(String text) {
        return restClient.post()
                .uri("https://slack.com/api/chat.postMessage")
                .header("Authorization", "Bearer " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "channel", adminDmUserId,
                        "text", text,
                        "unfurl_links", false,
                        "unfurl_media", false))
                .retrieve()
                .body(JsonNode.class);
    }

    private String describeError(JsonNode response) {
        return response != null ? response.path("error").asString("unknown") : "no response";
    }
}
