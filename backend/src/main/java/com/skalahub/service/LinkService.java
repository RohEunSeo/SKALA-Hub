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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LinkService {

    // 작성자 이름에서 "N반_이름"만 남기고 앞의 기수/캠퍼스(예: "4기_판교_")는 잘라냄 - 전원 공통이라 반복 표시할 필요가 없음
    private static final Pattern CLASS_AND_NAME = Pattern.compile("(\\d+반_.+)$");

    // 학습자료 하위 유형 배지 - frontend/src/constants/categories.js의 학습자료.tags 값과 동일해야 함
    private static final String STUDY_MATERIAL_CATEGORY = "학습자료";
    private static final Set<String> STUDY_MATERIAL_TYPE_TAGS = Set.of("영상", "블로그·글", "깃허브");

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
        // 학습자료 + 유형 필터는 게시글 원본 태그가 아니라 resolveTypeTag() 확정값 기준으로 걸러야
        // 관리자가 링크 단위로 고친 유형이 필터에도 반영됨 - 그래서 이 경우엔 SQL 단계에서 tag로
        // 거르지 않고(null로 넘김) 그룹을 다 만든 뒤 아래에서 typeTag로 다시 거른다
        boolean filterByResolvedType = STUDY_MATERIAL_CATEGORY.equals(category) && tag != null && !tag.isBlank();
        List<Post> posts = postService.findLinkedPosts(
                category, filterByResolvedType ? null : tag, keyword, author, date, campus, sort);
        Map<String, List<PostLinkEntry>> grouped = groupByUrl(postService.toResponses(posts));
        Map<String, LinkPreview> overrides = loadOverrides(grouped.keySet());

        // 그룹 순서는 재정렬하지 않고 LinkedHashMap의 첫 등장 순서를 그대로 씀 - posts가 이미 요청된 sort
        // 기준으로 정렬되어 있으므로, URL별 첫 등장 위치가 곧 그 정렬 기준에서의 대표 위치가 됨
        List<LinkGroupDto> allGroups = grouped.entrySet().stream()
                .map(entry -> buildGroup(entry.getKey(), entry.getValue(), overrides.get(entry.getKey())))
                .filter(g -> !filterByResolvedType || tag.equals(g.typeTag()))
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
        List<Post> posts = postService.findLinkedPosts(null, null, null, null, null, null, "latest");
        Map<String, List<PostLinkEntry>> grouped = groupByUrl(postService.toResponses(posts));
        Map<String, LinkPreview> overrides = loadOverrides(grouped.keySet());

        Map<String, Long> byCategory = new LinkedHashMap<>();
        Map<String, Long> byTag = new LinkedHashMap<>();
        for (Map.Entry<String, List<PostLinkEntry>> entry : grouped.entrySet()) {
            PostLinkEntry representative = pickRepresentative(entry.getValue());
            String category = representative.post.category();
            if (category != null) {
                byCategory.merge(category, 1L, Long::sum);
            }
            // 학습자료는 게시글 원본 태그가 아니라 resolveTypeTag() 확정값으로 집계 - 그래야 관리자가
            // 링크 단위로 고친 유형이 필터 옆 숫자에도 바로 반영됨(배지 표시 로직과 동일 기준)
            if (STUDY_MATERIAL_CATEGORY.equals(category)) {
                String resolved = resolveTypeTag(representative, overrides.get(entry.getKey()));
                if (resolved != null) {
                    byTag.merge(resolved, 1L, Long::sum);
                }
            } else {
                List<String> tags = representative.post.tags();
                if (tags != null) {
                    for (String tag : tags) {
                        byTag.merge(tag, 1L, Long::sum);
                    }
                }
            }
        }

        return new LinkCountsDto(grouped.size(), toCountList(byCategory), toCountList(byTag));
    }

    // 관리자 전용 "숨김" 갤러리 뷰 - 카테고리/기간 등 필터 없이 숨긴 링크 전체를 평소와 동일한 카드
    // 형태(buildGroup 그대로 재사용)로 반환. 개수가 적어(보통 수십 개 이내) 페이지네이션은 두지 않음
    public List<LinkGroupDto> getHiddenLinkGroups() {
        List<Post> posts = postService.findLinkedPosts(null, null, null, null, null, null, "latest");
        Map<String, List<PostLinkEntry>> grouped = groupByUrl(postService.toHiddenLinkResponses(posts));
        Map<String, LinkPreview> overrides = loadOverrides(grouped.keySet());

        return grouped.entrySet().stream()
                .map(entry -> buildGroup(entry.getKey(), entry.getValue(), overrides.get(entry.getKey())))
                .toList();
    }

    private List<CategoryCountDto> toCountList(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(e -> new CategoryCountDto(e.getKey(), e.getValue()))
                .toList();
    }

    // 게시글 응답 목록을 순회하며 URL별로 (게시글, 링크) 쌍을 묶는다 - 게시글 하나에 링크가 여러 개면
    // 그만큼 여러 그룹에 나눠 들어감. responses는 호출부가 toResponses()/toHiddenLinkResponses() 중
    // 필요한 걸로 미리 만들어서 넘김(평소엔 보이는 링크만, 관리자 숨김 뷰에선 숨긴 링크만)
    private Map<String, List<PostLinkEntry>> groupByUrl(List<PostResponse> responses) {
        Map<String, List<PostLinkEntry>> grouped = new LinkedHashMap<>();
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
                resolveTypeTag(representative, override),
                postDtos);
    }

    // 학습자료 하위 유형 배지 확정 순서: ①관리자가 링크 단위로 직접 지정한 값(있으면 무조건 우선) →
    // ②대표 게시글의 태그 중 학습자료 하위 태그와 겹치는 게 정확히 1개(모호하지 않은 경우만 - 게시글
    // 태그 수정이 자연스럽게 반영되게 함) → ③링크 자체의 서비스명으로 추정 → ④그래도 없으면 배지 없음.
    // 게시글 하나에 태그가 여러 개(영상+깃허브 등) 달려있을 수 있어, 그 목록을 그대로 쓰면 "이 링크
    // 하나"의 실제 유형과 어긋날 수 있어서 하나로 확정해서 내려준다
    private String resolveTypeTag(PostLinkEntry representative, LinkPreview override) {
        if (!STUDY_MATERIAL_CATEGORY.equals(representative.post.category())) {
            return null;
        }
        if (override != null && override.getAdminTypeTag() != null) {
            return override.getAdminTypeTag();
        }
        List<String> postTags = representative.post.tags();
        if (postTags != null) {
            List<String> matched =
                    postTags.stream().filter(STUDY_MATERIAL_TYPE_TAGS::contains).distinct().toList();
            if (matched.size() == 1) {
                return matched.get(0);
            }
        }
        return inferTypeTagFromService(representative.link.serviceName());
    }

    private String inferTypeTagFromService(String serviceName) {
        if (serviceName == null) {
            return null;
        }
        String lower = serviceName.toLowerCase();
        if (lower.contains("youtube")) {
            return "영상";
        }
        if (lower.contains("github")) {
            return "깃허브";
        }
        if (lower.contains("substack") || lower.contains("medium") || lower.contains("velog")) {
            return "블로그·글";
        }
        return null;
    }

    private static String shortenCreatorName(String userName) {
        Matcher matcher = CLASS_AND_NAME.matcher(userName);
        return matcher.find() ? matcher.group(1) : userName;
    }

    private record PostLinkEntry(PostResponse post, LinkPreviewDto link) {}
}
