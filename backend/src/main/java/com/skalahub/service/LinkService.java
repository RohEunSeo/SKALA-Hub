// 링크 모음 탭 - 게시글에 흩어진 링크를 URL 기준으로 그룹핑해서 제공
// 같은 URL이 여러 게시글에 걸쳐 있으면 카드 1개로 합치고, 반응수가 가장 높은 게시글을 "대표"로 삼아
// 통계(👍💬)는 그 대표 게시글의 실제 값을 그대로 보여줌(합산하면 왜곡될 수 있어서 지양)
package com.skalahub.service;

import com.skalahub.dto.CategoryCountDto;
import com.skalahub.dto.LinkCountsDto;
import com.skalahub.dto.LinkGroupDto;
import com.skalahub.dto.LinkGroupPageResponse;
import com.skalahub.dto.LinkGroupPostDto;
import com.skalahub.dto.LinkPreviewDto;
import com.skalahub.dto.PostResponse;
import com.skalahub.entity.LinkPreview;
import com.skalahub.entity.Post;
import com.skalahub.repository.LinkPreviewRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    // 작성자 이름에서 "N반_이름"만 남기고 앞의 기수/캠퍼스(예: "4기_판교_")는 잘라냄 - 전원 공통이라 반복 표시할 필요가 없음
    private static final Pattern CLASS_AND_NAME = Pattern.compile("(\\d+반_.+)$");

    private final PostService postService;
    private final LinkPreviewRepository linkPreviewRepository;

    public LinkService(PostService postService, LinkPreviewRepository linkPreviewRepository) {
        this.postService = postService;
        this.linkPreviewRepository = linkPreviewRepository;
    }

    public LinkGroupPageResponse getLinkGroups(
            String category,
            String tag,
            String keyword,
            String author,
            String date,
            String campus,
            String sort,
            int page,
            int size) {
        Map<String, List<PostLinkEntry>> grouped =
                groupByUrl(postService.findLinkedPosts(category, tag, keyword, author, date, campus, sort));
        Map<String, LinkPreview> overrides = loadOverrides(grouped.keySet());

        // 그룹 순서는 재정렬하지 않고 LinkedHashMap의 첫 등장 순서를 그대로 씀 - posts가 이미 요청된 sort
        // 기준으로 정렬되어 있으므로, URL별 첫 등장 위치가 곧 그 정렬 기준에서의 대표 위치가 됨
        List<LinkGroupDto> allGroups = grouped.entrySet().stream()
                .map(entry -> buildGroup(entry.getKey(), entry.getValue(), overrides.get(entry.getKey())))
                .toList();

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int totalElements = allGroups.size();
        int totalPages = (int) Math.ceil(totalElements / (double) safeSize);
        int from = Math.min(safePage * safeSize, totalElements);
        int to = Math.min(from + safeSize, totalElements);

        return new LinkGroupPageResponse(allGroups.subList(from, to), safePage, safeSize, totalElements, totalPages);
    }

    // 홈 화면/카테고리 칩에 표시할 개수 - 게시글 수나 링크 언급 횟수가 아니라 실제 카드(URL 그룹) 수 기준.
    // 필터 없이 전체를 대상으로 한 번 그룹핑해서 카테고리/태그별로 그룹 수를 센다(그룹당 대표 게시글의 카테고리/태그로 집계)
    public LinkCountsDto getCounts() {
        Map<String, List<PostLinkEntry>> grouped =
                groupByUrl(postService.findLinkedPosts(null, null, null, null, null, null, "latest"));

        Map<String, Long> byCategory = new LinkedHashMap<>();
        Map<String, Long> byTag = new LinkedHashMap<>();
        for (List<PostLinkEntry> entries : grouped.values()) {
            PostLinkEntry representative = pickRepresentative(entries);
            String category = representative.post.category();
            if (category != null) {
                byCategory.merge(category, 1L, Long::sum);
            }
            List<String> tags = representative.post.tags();
            if (tags != null) {
                for (String tag : tags) {
                    byTag.merge(tag, 1L, Long::sum);
                }
            }
        }

        return new LinkCountsDto(grouped.size(), toCountList(byCategory), toCountList(byTag));
    }

    private List<CategoryCountDto> toCountList(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(e -> new CategoryCountDto(e.getKey(), e.getValue()))
                .toList();
    }

    // 링크 있는 게시글 전체를 순회하며 URL별로 (게시글, 링크) 쌍을 묶는다 - 게시글 하나에 링크가 여러 개면
    // 그만큼 여러 그룹에 나눠 들어감
    private Map<String, List<PostLinkEntry>> groupByUrl(List<Post> posts) {
        Map<String, List<PostLinkEntry>> grouped = new LinkedHashMap<>();
        // link_previews 오버라이드를 게시글마다 따로 조회하지 않고 전체를 한 번에 배치 조회(N+1 방지)
        List<PostResponse> responses = postService.toResponses(posts);

        for (PostResponse response : responses) {
            for (LinkPreviewDto link : response.links()) {
                String url = link.fromUrl() != null ? link.fromUrl() : link.titleLink();
                if (url == null) {
                    continue;
                }
                grouped.computeIfAbsent(url, k -> new ArrayList<>()).add(new PostLinkEntry(response, link));
            }
        }
        return grouped;
    }

    private Map<String, LinkPreview> loadOverrides(Iterable<String> urls) {
        Map<String, LinkPreview> overrides = new LinkedHashMap<>();
        linkPreviewRepository.findAllById(urls).forEach(lp -> overrides.put(lp.getUrl(), lp));
        return overrides;
    }

    // 그룹 내 반응수가 가장 높은 게시글을 "대표"로 삼음 - 카드 앞면 통계/카테고리 표시에 사용
    private PostLinkEntry pickRepresentative(List<PostLinkEntry> entries) {
        return entries.stream()
                .max(Comparator.comparing(
                        (PostLinkEntry e) -> e.post.reactionCount() == null ? 0 : e.post.reactionCount()))
                .orElse(entries.get(0));
    }

    private LinkGroupDto buildGroup(String url, List<PostLinkEntry> entries, LinkPreview override) {
        PostLinkEntry representative = pickRepresentative(entries);

        List<String> creators;
        if (override != null && override.getAdminCreators() != null) {
            // 관리자가 직접 입력한 값도 "N기_캠퍼스_" 형태를 그대로 붙여넣었을 수 있어 동일하게 잘라줌
            // (편집 폼이 예전엔 축약 전 이름으로 프리필됐어서, 그대로 저장된 값이 남아있을 수 있음)
            creators = Arrays.stream(override.getAdminCreators().split("\\s*,\\s*"))
                    .map(LinkService::shortenCreatorName)
                    .toList();
        } else {
            creators = entries.stream()
                    .map(e -> e.post.userName())
                    .filter(Objects::nonNull)
                    .map(LinkService::shortenCreatorName)
                    .distinct()
                    .toList();
        }

        List<LinkGroupPostDto> postDtos = entries.stream()
                .map(e -> new LinkGroupPostDto(
                        e.post.id(),
                        e.post.userName(),
                        e.post.userAvatarUrl(),
                        e.post.reactionCount(),
                        e.post.replyCount(),
                        e.post.createdAt()))
                .toList();

        LinkPreviewDto link = representative.link;
        return new LinkGroupDto(
                url,
                link.titleLink() != null ? link.titleLink() : url,
                link.title(),
                link.text(),
                link.imageUrl(),
                link.serviceName(),
                creators,
                representative.post.reactionCount(),
                representative.post.replyCount(),
                representative.post.category(),
                postDtos);
    }

    private static String shortenCreatorName(String userName) {
        Matcher matcher = CLASS_AND_NAME.matcher(userName);
        return matcher.find() ? matcher.group(1) : userName;
    }

    private record PostLinkEntry(PostResponse post, LinkPreviewDto link) {}
}
