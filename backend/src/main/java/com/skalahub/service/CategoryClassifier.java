// 게시글 카테고리 자동 분류 (Claude Haiku API + 교수님 규칙)
package com.skalahub.service;

import com.skalahub.entity.Post;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
            
            카테고리 (하나만 선택):
            - 개발 툴·환경: VS Code 확장, 터미널, Git 도구, Mac 앱(Karabiner, Rectangle, Raycast 등), 개발 생산성 도구, 맥 환경 설정 관련
            - 학습자료: 강의, 블로그, 논문, 깃허브 레포, 유튜브 영상 등
            - 자격증·취업: SQLD, 정처기, SKCT, 채용 정보, 면접 후기
            - 교육생 서비스: 교육생이 직접 만든 서비스/앱/웹사이트 공유
            - 교수님: 교수님 또는 전임교수가 작성한 글
            - 기타: 위에 해당 없는 것
            
            태그 (학습자료일 때는 해당되는 것 모두 선택 / 기타일 때는 아래 4가지 중 하나만 선택, 반드시 하나는 선택 / 그 외 카테고리는 태그 없음, tags: []):
            - 영상: youtube.com 링크 포함된 것
            - 블로그·글: velog, tistory, medium, 블로그, 아티클 링크
            - 깃허브: github.com 링크 포함된 것
            - 인사이트·경험 공유: 해커톤 회고, 스터디/멘토링 모집, 유용한 툴·강의·아티클·프롬프트 추천, 공부 노하우, 트렌드 정보 공유 (키워드: 인사이트, 팁, 추천, 공유, 회고, 스터디, 모임, 후기)
            - 오류 해결: 개발 환경 설정 에러, 프로그램/툴(Zoom, 구글 등) 사용 중 겪는 문제와 해결 방법, 트러블슈팅 공유 (키워드: 오류, 에러, 설정, 해결, zshrc, 에코, passkey, 버그)
            - 분실물: 캠퍼스(화장실, 사물함, 데스크 등) 내 물건 분실 및 습득 안내 (키워드: 분실, 습득, 텀블러, 화장품, 잃어버린, 찾으세요, 데스크, 운영실)
            - 그 외: 기타 카테고리인데 위 3가지(인사이트·경험 공유/오류 해결/분실물) 중 어디에도 해당하지 않는 것

            JSON만 반환:
            {"category": "개발 툴·환경", "tags": []}
            
            게시글: %s
            """;

    private static final String JSON_SCHEMA = """
            {
            "type": "object",
            "properties": {
                "category": {
                "type": "string",
                "enum": ["개발 툴·환경","학습자료","자격증·취업","교육생 서비스","교수님","기타"]
                },
                "tags": {
                "type": "array",
                "items": {
                    "type": "string",
                    "enum": ["영상","블로그·글","깃허브","인사이트·경험 공유","오류 해결","분실물","그 외"]
                }
                }
            },
            "required": ["category", "tags"],
            "additionalProperties": false
            }
            """;
    // 카테고리별로 허용되는 태그만 남기고 나머지는 버림 - Claude가 프롬프트를 무시하고
    // 학습자료/기타가 아닌 카테고리에 기타 하위태그(인사이트·경험 공유 등)를 붙이는 경우 방지
    private static final Set<String> STUDY_TAGS = Set.of("영상", "블로그·글", "깃허브");
    private static final Set<String> ETC_TAGS = Set.of("인사이트·경험 공유", "오류 해결", "분실물", "그 외");

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
            String category = result.path("category").asString("기타");
            post.setCategory(category);
            List<String> tags = new ArrayList<>();
            result.path("tags").forEach(tag -> tags.add(tag.asString()));
            post.setTags(filterTagsForCategory(category, tags));
        } catch (Exception e) {
            // 실패 시 category를 비워둬서 다음 스케줄 동기화 때 재시도되게 함
            log.warn("게시글 분류 실패 (slackTs={})", post.getSlackTs(), e);
        }
    }

    private List<String> filterTagsForCategory(String category, List<String> tags) {
        Set<String> allowed = switch (category) {
            case "학습자료" -> STUDY_TAGS;
            case "기타" -> ETC_TAGS;
            default -> Set.of();
        };
        return tags.stream().filter(allowed::contains).toList();
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
