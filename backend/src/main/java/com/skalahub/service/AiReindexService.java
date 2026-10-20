// 새 글이 저장·분류·AI 제목 생성까지 끝난 직후 AI 서버(RAG)에 "이 글 색인해줘"라고 알린다.
// @Async로 던져서 AI 서버가 느리거나(Cloud Run 콜드스타트) 꺼져있어도 슬랙 동기화 자체는
// 안 막히게 한다 - 실패해도 다음 전체 재동기화(새벽 4시)나 수동 재색인으로 복구된다.
package com.skalahub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiReindexService {

    private static final Logger log = LoggerFactory.getLogger(AiReindexService.class);

    private final RestClient restClient = RestClient.create();

    private final String aiApiBaseUrl;
    private final String jwtSecret;

    public AiReindexService(
            @Value("${ai.api-base-url}") String aiApiBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
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
            restClient.post()
                    .uri(aiApiBaseUrl + "/api/internal/reindex-post?post_id=" + postId)
                    .header("X-Internal-Secret", jwtSecret)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 재인덱싱 실패가 글 저장 자체를 막으면 안 된다 - 로그만 남긴다.
            log.warn("AI 서버 재인덱싱 요청 실패 (postId={})", postId, e);
        }
    }
}
