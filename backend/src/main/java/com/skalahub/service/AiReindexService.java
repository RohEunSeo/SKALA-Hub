// 새 글이 저장·분류·AI 제목 생성까지 끝난 직후 AI 서버(RAG)에 "이 글 색인해줘"라고 알린다.
// @Async로 던져서 AI 서버가 느리거나(Cloud Run 콜드스타트) 꺼져있어도 슬랙 동기화 자체는
// 안 막히게 한다 - 결과(성공/스킵/실패)는 관리자 DM(SlackDmNotificationService)으로 알려서
// 실시간 색인이 실제로 도는지 확인하고, 실패하면 수동으로 고칠 수 있게 한다.
package com.skalahub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class AiReindexService {

    private static final Logger log = LoggerFactory.getLogger(AiReindexService.class);

    private final RestClient restClient = RestClient.create();

    private final SlackDmNotificationService slackDmNotificationService;
    private final String aiApiBaseUrl;
    private final String jwtSecret;

    public AiReindexService(
            SlackDmNotificationService slackDmNotificationService,
            @Value("${ai.api-base-url}") String aiApiBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.slackDmNotificationService = slackDmNotificationService;
        this.aiApiBaseUrl = aiApiBaseUrl;
        this.jwtSecret = jwtSecret;
    }

    @Async
    public void reindexAsync(Long postId) {
        // 로컬 개발 등 AI_API_BASE_URL을 안 넣은 환경에서는 조용히 건너뛴다.
        if (aiApiBaseUrl == null || aiApiBaseUrl.isBlank()) {
            return;
        }
        try {
            JsonNode response = restClient.post()
                    .uri(aiApiBaseUrl + "/api/internal/reindex-post?post_id=" + postId)
                    .header("X-Internal-Secret", jwtSecret)
                    .retrieve()
                    .body(JsonNode.class);
            int chunks = response != null ? response.path("chunks").asInt(-1) : -1;
            slackDmNotificationService.sendReindexResult(postId, chunks);
        } catch (Exception e) {
            // 재인덱싱 실패가 글 저장 자체를 막으면 안 된다 - 로그만 남기고 DM으로 알린다.
            log.warn("AI 서버 재인덱싱 요청 실패 (postId={})", postId, e);
            slackDmNotificationService.sendReindexFailure(postId, describeError(e));
        }
    }

    // DM에 원인을 한 줄로 요약 - 관리자가 뭘 봐야 할지 바로 알 수 있게
    private String describeError(Exception e) {
        if (e instanceof HttpClientErrorException.Unauthorized) {
            return "인증 실패 - JWT_SECRET이 AI 서버와 다를 수 있음";
        }
        if (e instanceof HttpServerErrorException) {
            return "AI 서버 내부 오류";
        }
        if (e instanceof ResourceAccessException) {
            return "AI 서버 응답 없음 (연결 실패/타임아웃)";
        }
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : truncate(msg, 150);
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
