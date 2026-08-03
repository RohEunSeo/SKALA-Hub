// 피드 게시글 조회 (검색/필터/매핑)
package com.skalahub.service;

import com.skalahub.dto.FileAttachmentDto;
import com.skalahub.dto.LinkPreviewDto;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.dto.ReplyResponse;
import com.skalahub.entity.Post;
import com.skalahub.entity.Reply;
import com.skalahub.entity.User;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.ReplyRepository;
import com.skalahub.repository.UserRepository;
import com.skalahub.util.CampusResolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PostService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> VALID_SORTS = Set.of("latest", "popular", "oldest");

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final String channelId;
    private final String workspaceUrl;

    public PostService(
            PostRepository postRepository,
            ReplyRepository replyRepository,
            UserRepository userRepository,
            @Value("${slack.channel-id}") String channelId,
            @Value("${slack.workspace-url}") String workspaceUrl) {
        this.postRepository = postRepository;
        this.replyRepository = replyRepository;
        this.userRepository = userRepository;
        this.channelId = channelId;
        this.workspaceUrl = workspaceUrl;
    }

    public PostPageResponse search(
            String category,
            String tag,
            String keyword,
            String author,
            String date,
            String campus,
            String sort,
            int page,
            int size) {
        LocalDateTime[] range = resolveDateRange(date);
        boolean campusActive = campus != null && !campus.isBlank();
        List<String> campusSlackIds;
        if (campusActive) {
            String targetFloor = campus;
            campusSlackIds = userRepository.findAll().stream()
                    .filter(u -> targetFloor.equals(CampusResolver.resolveFloor(u.getClassNum())))
                    .map(User::getSlackId)
                    .toList();
            if (campusSlackIds.isEmpty()) {
                campusSlackIds = List.of("__no_match__");
            }
        } else {
            campusSlackIds = List.of("__unused__");
        }

        Page<Post> result = postRepository.search(
                blankToNull(category),
                blankToNull(tag),
                blankToNull(keyword),
                blankToNull(author),
                range[0],
                range[1],
                campusActive,
                campusSlackIds,
                normalizeSort(sort),
                PageRequest.of(page, size));

        return wrapPage(result);
    }

    private String normalizeSort(String sort) {
        return VALID_SORTS.contains(sort) ? sort : "latest";
    }

    // Home/MyPage 등 다른 화면에서도 같은 매핑 로직을 재사용하기 위한 공개 메서드
    public PostPageResponse wrapPage(Page<Post> result) {
        List<PostResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new PostPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                postRepository.findLastSyncedAt());
    }

    public PostResponse getPost(Long id) {
        Post post = postRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"));
        return toResponse(post);
    }

    // 존재 확인 쿼리를 따로 두지 않고 바로 조회 - 프론트에서 이미 로드된 게시글에 대해서만 호출되므로
    // 없는 postId가 들어올 일이 없고, DB가 원격(Supabase)이라 왕복 쿼리를 하나라도 줄이는 게 체감 속도에 큼
    public List<ReplyResponse> getReplies(Long postId) {
        return replyRepository.findByPost_IdOrderByCreatedAtAsc(postId).stream()
                .map(this::toReplyResponse)
                .toList();
    }

    private ReplyResponse toReplyResponse(Reply reply) {
        return new ReplyResponse(
                reply.getId(),
                reply.getUserName(),
                reply.getUserAvatarUrl(),
                reply.getContent(),
                reply.getCreatedAt());
    }

    // Home 순위보드 등 다른 서비스에서도 재사용
    public PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getUserName(),
                post.getUserAvatarUrl(),
                post.getIsInstructor(),
                post.getContent(),
                post.getCategory(),
                post.getTags(),
                post.getReactionCount(),
                post.getReplyCount(),
                post.getIsPinned(),
                post.getIsEdited(),
                post.getCreatedAt(),
                buildPermalink(post.getSlackTs()),
                parseAttachments(post.getAttachments()),
                parseFiles(post.getFiles()));
    }

    private String buildPermalink(String slackTs) {
        if (slackTs == null || slackTs.isBlank()) {
            return null;
        }
        return workspaceUrl + "/archives/" + channelId + "/p" + slackTs.replace(".", "");
    }

    private List<LinkPreviewDto> parseAttachments(String rawJson) {
        List<LinkPreviewDto> previews = new ArrayList<>();
        if (rawJson == null || rawJson.isBlank()) {
            return previews;
        }
        for (JsonNode node : jsonMapper.readTree(rawJson)) {
            previews.add(new LinkPreviewDto(
                    node.path("title").asString(null),
                    node.path("title_link").asString(node.path("from_url").asString(null)),
                    node.path("text").asString(null),
                    node.path("image_url").asString(node.path("thumb_url").asString(null)),
                    node.path("from_url").asString(null),
                    node.path("service_name").asString(null)));
        }
        return previews;
    }

    private List<FileAttachmentDto> parseFiles(String rawJson) {
        List<FileAttachmentDto> files = new ArrayList<>();
        if (rawJson == null || rawJson.isBlank()) {
            return files;
        }
        for (JsonNode node : jsonMapper.readTree(rawJson)) {
            String mimetype = node.path("mimetype").asString("");
            boolean isImage = mimetype.startsWith("image/");
            String urlPrivate = node.path("url_private").asString(null);
            files.add(new FileAttachmentDto(
                    node.path("name").asString(node.path("title").asString("파일")),
                    node.path("filetype").asString(null),
                    mimetype,
                    isImage,
                    node.path("size").asLong(0) > 0 ? node.path("size").asLong() : null,
                    isImage && urlPrivate != null ? buildProxyUrl(urlPrivate) : null));
        }
        return files;
    }

    private String buildProxyUrl(String urlPrivate) {
        return "/api/files/proxy?url=" + URLEncoder.encode(urlPrivate, StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    // date: "today"/"week"/"month"/"yyyy-MM" - 인식 불가하면 필터 없음 처리
    // Home 순위보드(이번주 필터)에서도 재사용하기 위해 public
    public LocalDateTime[] resolveDateRange(String date) {
        if (date == null || date.isBlank()) {
            return new LocalDateTime[] {null, null};
        }
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from;
        LocalDate to;
        switch (date) {
            case "today" -> {
                from = today;
                to = today.plusDays(1);
            }
            case "week" -> {
                from = today.with(DayOfWeek.MONDAY);
                to = from.plusWeeks(1);
            }
            case "month" -> {
                from = today.withDayOfMonth(1);
                to = from.plusMonths(1);
            }
            default -> {
                try {
                    YearMonth ym = YearMonth.parse(date);
                    from = ym.atDay(1);
                    to = from.plusMonths(1);
                } catch (Exception e) {
                    return new LocalDateTime[] {null, null};
                }
            }
        }
        return new LocalDateTime[] {from.atStartOfDay(), to.atStartOfDay()};
    }
}
