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
        // FRONTEND_URL 환경변수 끝에 "/"가 붙어있어도(예: https://x.com/) 링크가 "//posts/.."로
        // 겹치지 않도록 방어
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.testMode = testMode;
    }

    // 글 감지 직후 "동기화 중" 댓글을 단다. notifySyncSuccess/Failure는 이 댓글을 고쳐쓰지 않고
    // 완전히 별도의 새 댓글을 달아서, 스레드에 "동기화 중 → 완료"가 각각 남아있도록 한다.
    public void notifySyncStarted(String threadTs) {
        postThreadReply(threadTs, "🔄 SKALA-Hub 동기화 중입니다 . . .");
    }

    public void notifySyncSuccess(String threadTs, Long postId) {
        String timestamp = formatTimestamp(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        String message = "✅ SKALA-Hub에 동기화되었습니다! (" + timestamp + ")\n"
                + "🔗 바로가기: " + frontendUrl + "/posts/" + postId + "\n"
                + "📌 버그 제보·건의사항·피드백은 익명 설문폼으로 편하게 남겨주세요! " + SURVEY_FORM_URL;
        postThreadReply(threadTs, message);
    }

    public void notifySyncFailure(String threadTs) {
        postThreadReply(threadTs, "⚠️ SKALA-Hub 동기화에 실패했습니다. 관리자에게 문의해주세요!");
    }

    // 댓글 전송 실패가 동기화 스케줄러 자체를 멈추면 안 되므로 어떤 예외도 밖으로 던지지 않음
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
                    .body(Map.of("channel", channelId, "thread_ts", threadTs, "text", text))
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
