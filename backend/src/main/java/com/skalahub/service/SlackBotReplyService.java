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

    // TEST_MODE에서 postThreadReply가 실제로는 ts를 못 받아오므로 downstream(notifySyncSuccess/Failure)이
    // "시작 알림 자체가 실패했다"고 오인해 로그를 건너뛰지 않도록 쓰는 더미 값
    private static final String TEST_MODE_COMMENT_TS = "TEST_MODE_TS";

    // 버그 제보/건의사항/피드백 접수용 익명 설문폼 - 비밀 정보가 아니므로 상수로 고정
    private static final String SURVEY_FORM_URL = "https://tally.so/r/gDX4G4";

    private final RestClient restClient = RestClient.create();

    private final String botToken;
    private final String channelId;
    private final String frontendUrl;
    private final boolean testMode;

    public SlackBotReplyService(
            @Value("${slack.bot-token:}") String botToken,
            @Value("${slack.channel-id}") String channelId,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${test-mode:false}") boolean testMode) {
        this.botToken = botToken;
        this.channelId = channelId;
        this.frontendUrl = frontendUrl;
        this.testMode = testMode;
    }

    // 글 감지 직후 "동기화 중" 댓글을 먼저 달고, 나중에 notifySyncSuccess/Failure에서 이 댓글을 수정한다.
    // 반환값은 이 댓글 자신의 ts(수정 대상 식별자) - 전송 자체가 실패하면 null
    public String notifySyncStarted(String threadTs) {
        return postThreadReply(threadTs, "🔄 SKALA-Hub 동기화 중입니다 . . .");
    }

    public void notifySyncSuccess(String commentTs, Long postId) {
        String timestamp = formatTimestamp(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        String message = "✅ SKALA-Hub에 동기화되었습니다! (" + timestamp + ")\n"
                + "🔗 바로가기: " + frontendUrl + "/posts/" + postId + "\n"
                + "📌 버그 제보·건의사항·피드백은 익명 설문폼으로 편하게 남겨주세요! " + SURVEY_FORM_URL;
        updateReply(commentTs, message);
    }

    public void notifySyncFailure(String commentTs) {
        updateReply(commentTs, "⚠️ SKALA-Hub 동기화에 실패했습니다. 관리자에게 문의해주세요!");
    }

    // 댓글 전송 실패가 동기화 스케줄러 자체를 멈추면 안 되므로 어떤 예외도 밖으로 던지지 않음
    private String postThreadReply(String threadTs, String text) {
        if (testMode) {
            log.info("[TEST_MODE] 슬랙 댓글 전송 스킵 - threadTs={}, text={}", threadTs, text);
            return TEST_MODE_COMMENT_TS;
        }
        try {
            JsonNode response = restClient.post()
                    .uri("https://slack.com/api/chat.postMessage")
                    .header("Authorization", "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("channel", channelId, "thread_ts", threadTs, "text", text))
                    .retrieve()
                    .body(JsonNode.class);
            if (!response.path("ok").asBoolean(false)) {
                log.error("슬랙 스레드 댓글 전송 실패 (threadTs={}): {}", threadTs, describeError(response));
                return null;
            }
            return response.path("ts").asString(null);
        } catch (Exception e) {
            log.error("슬랙 스레드 댓글 전송 실패 (threadTs={})", threadTs, e);
            return null;
        }
    }

    // 댓글 수정 실패도 동기화 흐름을 막으면 안 되므로 어떤 예외도 밖으로 던지지 않음
    private void updateReply(String commentTs, String text) {
        if (testMode) {
            log.info("[TEST_MODE] 슬랙 댓글 수정 스킵 - commentTs={}, text={}", commentTs, text);
            return;
        }
        if (commentTs == null) {
            log.warn("수정할 슬랙 댓글이 없어 건너뜁니다 (동기화 중 알림 전송에 실패했던 것으로 보임) - text={}", text);
            return;
        }
        try {
            JsonNode response = restClient.post()
                    .uri("https://slack.com/api/chat.update")
                    .header("Authorization", "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("channel", channelId, "ts", commentTs, "text", text))
                    .retrieve()
                    .body(JsonNode.class);
            if (!response.path("ok").asBoolean(false)) {
                log.error("슬랙 댓글 수정 실패 (commentTs={}): {}", commentTs, describeError(response));
            }
        } catch (Exception e) {
            log.error("슬랙 댓글 수정 실패 (commentTs={})", commentTs, e);
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
