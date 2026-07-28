// 게시글 카테고리 자동 분류 (Claude Haiku API + 교수님 규칙)
package com.skalahub.service;

import com.skalahub.entity.Post;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CategoryClassifier {

    private static final Logger log = LoggerFactory.getLogger(CategoryClassifier.class);

    private static final String PROMPT_TEMPLATE = """
            다음 슬랙 게시글을 분류해줘. JSON으로만 답해줘.
            카테고리: 개발도구/학습자료/취업자격증/교육생서비스/교수님/기타
            태그: 영상/아티클/깃허브 (학습자료일 때만, 해당되는 것만)

            {"category": "개발도구", "tags": []}

            게시글: %s
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "category": {"type": "string", "enum": ["개발도구","학습자료","취업자격증","교육생서비스","교수님","기타"]},
                "tags": {"type": "array", "items": {"type": "string", "enum": ["영상","아티클","깃허브"]}}
              },
              "required": ["category", "tags"],
              "additionalProperties": false
            }
            """;

    private final RestClient restClient = RestClient.create();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final String apiKey;
    private final String model;

    public CategoryClassifier(
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public void classify(Post post) {
        if (isInstructor(post.getUserName())) {
            post.setCategory("교수님");
            post.setTags(List.of());
            return;
        }
        if (post.getContent() == null || post.getContent().isBlank()) {
            post.setCategory("기타");
            post.setTags(List.of());
            return;
        }

        try {
            JsonNode result = callClaude(post.getContent());
            post.setCategory(result.path("category").asString("기타"));
            List<String> tags = new ArrayList<>();
            result.path("tags").forEach(tag -> tags.add(tag.asString()));
            post.setTags(tags);
        } catch (Exception e) {
            // 실패 시 category를 비워둬서 다음 스케줄 동기화 때 재시도되게 함
            log.warn("게시글 분류 실패 (slackTs={})", post.getSlackTs(), e);
        }
    }

    private boolean isInstructor(String userName) {
        return userName != null && (userName.contains("교수") || userName.contains("전임"));
    }

    private JsonNode callClaude(String content) {
        String prompt = PROMPT_TEMPLATE.formatted(content);
        JsonNode schema = jsonMapper.readTree(JSON_SCHEMA);

        JsonNode response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.createObjectNode()
                        .put("model", model)
                        .put("max_tokens", 256)
                        .set("messages", jsonMapper.createArrayNode()
                                .add(jsonMapper.createObjectNode()
                                        .put("role", "user")
                                        .put("content", prompt)))
                        .set("output_config", jsonMapper.createObjectNode()
                                .set("format", jsonMapper.createObjectNode()
                                        .put("type", "json_schema")
                                        .set("schema", schema))))
                .retrieve()
                .body(JsonNode.class);

        String text = response.path("content").path(0).path("text").asString();
        return jsonMapper.readTree(text);
    }
}
