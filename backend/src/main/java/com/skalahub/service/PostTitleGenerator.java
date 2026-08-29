// 게시글 AI 한 줄 제목/요약 생성 (Claude Haiku API). 본문에 요약할 텍스트가 부실하면(링크만 달랑
// 올린 글) attachments(슬랙이 만들어준 og:title/description) → 그마저 부실하면 실제 링크 페이지를
// 직접 읽어서 요약 재료로 삼는다. CategoryClassifier.java와 동일한 REST 직접 호출 구조를 따른다.
package com.skalahub.service;

import com.skalahub.entity.Post;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PostTitleGenerator {

    private static final Logger log = LoggerFactory.getLogger(PostTitleGenerator.class);

    private static final int FETCH_TIMEOUT_MS = 4000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; SkalaHubBot/1.0)";
    private static final int MAX_FETCHED_CHARS = 4000;
    private static final int MAX_TITLE_CHARS = 60;
    private static final int MIN_OWN_TEXT_CHARS = 15;

    // renderSlackText.js가 링크를 렌더링할 때 쓰는 슬랙 링크 마크업 <url|label> / <url>과 동일한 패턴
    private static final Pattern LINK_TAG = Pattern.compile("<(https?://[^|>\\s]+)(?:\\|[^>]+)?>");

    private static final String PROMPT_TEMPLATE = """
            너는 SKALA 부트캠프 교육생들의 슬랙 게시글을 한 줄로 요약하는 편집자야.
            아래 예시를 참고해서 게시글에 어울리는 한 줄 제목을 만들어줘.

            [좋은 제목의 기준]
            1. 게시글 원문의 단어·표현을 최대한 그대로 활용한다 (창작하지 않고 압축한다)
            2. 40자 이내. 완결된 문장이 아니어도 되고, 명사형으로 끝나도 된다 (예: "~공유", "~업데이트", "~소개")
            3. 여러 내용이 섞여 있으면 가장 핵심적인 한 가지로 압축한다
            4. 링크 주소(URL), 이모지, 따옴표는 넣지 않는다
            5. 존댓말을 쓰지 않고 담백한 사실 서술체로 쓴다
            6. 전문용어를 최소화해서 누가 읽어도 바로 무슨 내용인지 알 수 있게 쓴다

            [예시 1 - 링크만 공유된 글]
            게시글: (URL만 있음) https://calv.info/small-models-have-arrived
            페이지 발췌: 소형 오픈소스 AI 모델들이 최근 성능이 크게 좋아지면서 비용 대비 효율이 높아졌고,
            이게 B2C 서비스나 사내 업무 자동화에 실질적으로 쓰일 수 있는 시점이 왔다는 내용...
            core_point: 소형 AI 모델의 성능 향상과 비용 절감이 실제 서비스 적용에 미치는 영향을 다룬 글
            title: 소형 AI 모델의 성능 향상과 비용 절감이 B2C 서비스 개발에 미치는 영향

            [예시 2 - 핵심 내용을 직접 정리해둔 기술 공유 글]
            게시글: "지난 3월, axios의 npm 관리자 계정이 탈취되어 악성 버전(1.14.1, 0.30.4)이 배포된 적이
            있습니다... [참고할 만한 부분] npm install 시 버전이 갑자기 바뀌면 한 번 확인하는 습관..."
            core_point: axios npm 계정 탈취로 악성 버전이 배포됐던 사건과 설치 시 주의할 점
            title: axios npm 계정 탈취로 인한 악성 버전 배포 사건과 설치 시 주의사항

            [예시 3 - 서비스 업데이트 공지]
            게시글: "ssk-zoom의 SKALA 메뉴바 아이콘이 4버전으로 업데이트됐어요. 개인 커스텀 기능, 크기 조절,
            png/jpg 지원이 추가됐습니다..."
            core_point: 교육생이 만든 SKALA 메뉴바 아이콘 앱이 커스텀 기능을 담아 업데이트됨
            title: SKALA 메뉴바 아이콘 앱 커스텀 기능 업데이트

            [예시 4 - 여러 포인트가 섞인 소개 글]
            게시글: "AX Signal - SK 계열사 AI/AX 소식을 모아 평일 아침 메일로 보내드리는 뉴스레터입니다.
            제목만 던지지 않고 왜 중요한지 한 줄씩 붙입니다..."
            core_point: SK 계열사 AI/AX 소식을 매일 아침 메일로 정리해 보내주는 뉴스레터 소개
            title: SK 계열사 AI/AX 소식을 매일 아침 정리해주는 뉴스레터 AX Signal 소개

            이제 아래 게시글로 core_point를 먼저 정리한 뒤 title을 만들어줘. JSON으로만 답해.

            게시글: %s
            """;

    private static final String JSON_SCHEMA = """
            {
            "type": "object",
            "properties": {
                "core_point": { "type": "string" },
                "title": { "type": "string" }
            },
            "required": ["core_point", "title"],
            "additionalProperties": false
            }
            """;

    private final RestClient restClient = RestClient.create();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final String apiKey;
    private final String model;

    public PostTitleGenerator(
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public void generateTitle(Post post) {
        String material = buildMaterial(post);
        if (material == null) {
            // 본문도 링크도 없는 글(사진만 있는 글 등) - 요약할 재료가 없음. null이 아니라 빈 문자열로
            // 표시해서 "생성 시도했지만 만들 게 없었음" 상태로 확정 - null로 남겨두면 매 동기화/일괄
            // 생성 때마다 똑같이 재시도하고, 관리자 화면의 "남은 개수"도 영원히 0이 되지 않음
            post.setAiTitle("");
            return;
        }
        try {
            JsonNode result = callClaude(material);
            String title = result.path("title").asString(null);
            if (title != null && !title.isBlank()) {
                post.setAiTitle(truncate(title.trim(), MAX_TITLE_CHARS));
            }
        } catch (Exception e) {
            log.warn("게시글 제목 생성 실패 (postId={})", post.getId(), e);
        }
    }

    private String buildMaterial(Post post) {
        String content = post.getContent();
        String ownText = content == null ? "" : LINK_TAG.matcher(content).replaceAll("").trim();

        if (ownText.length() >= MIN_OWN_TEXT_CHARS) {
            return content;
        }

        String url = extractFirstUrl(content);
        String fromAttachment = attachmentText(post.getAttachments());
        if (fromAttachment != null && fromAttachment.length() >= MIN_OWN_TEXT_CHARS) {
            return "(URL만 공유된 글)\n링크: " + (url == null ? "" : url) + "\n" + fromAttachment;
        }

        if (url != null) {
            String fetched = fetchPageText(url);
            if (fetched != null) {
                return "(URL만 공유된 글)\n링크: " + url + "\n페이지 발췌: " + fetched;
            }
        }

        return (ownText.isBlank() && url == null) ? null : content;
    }

    private String extractFirstUrl(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = LINK_TAG.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    // 슬랙이 이미 만들어준 og:title/description (Post.attachments 원본 JSON, PostService.parseAttachments와 동일한 키)
    private String attachmentText(String rawAttachments) {
        if (rawAttachments == null || rawAttachments.isBlank()) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (JsonNode node : jsonMapper.readTree(rawAttachments)) {
                String title = node.path("title").asString(null);
                String text = node.path("text").asString(null);
                if (title != null) {
                    sb.append("페이지 제목: ").append(title).append('\n');
                }
                if (text != null) {
                    sb.append(text).append('\n');
                }
            }
            String result = sb.toString().trim();
            return result.isBlank() ? null : result;
        } catch (Exception e) {
            return null;
        }
    }

    // LinkPreviewFetchService와 동일한 timeout/User-Agent - og 메타태그가 아니라 본문 영역 텍스트를 읽음
    private String fetchPageText(String url) {
        try {
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(FETCH_TIMEOUT_MS).get();
            String text = doc.select("article, main, [role=main]").text();
            if (text.isBlank() && doc.body() != null) {
                text = doc.body().text();
            }
            return text.isBlank() ? null : truncate(text, MAX_FETCHED_CHARS);
        } catch (Exception e) {
            log.debug("제목 생성용 페이지 fetch 실패: {} ({})", url, e.toString());
            return null;
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private JsonNode callClaude(String material) {
        String prompt = PROMPT_TEMPLATE.formatted(material);
        JsonNode schema = jsonMapper.readTree(JSON_SCHEMA);

        JsonNode response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.createObjectNode()
                        .put("model", model)
                        .put("max_tokens", 300)
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
