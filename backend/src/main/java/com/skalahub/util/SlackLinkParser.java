// 슬랙 원문 본문에 남아있는 링크 마크업(<url|label> 또는 <url>) 추출 - PostService(링크 모음 응답)와
// LinkPreviewFetchService(동기화 시점 og:title fetch 대상 선정)가 동일한 정규식을 공유하기 위한 유틸
package com.skalahub.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlackLinkParser {

    private static final Pattern LINK_PATTERN = Pattern.compile("<(https?://[^|>\\s]+)(?:\\|([^>]+))?>");

    private SlackLinkParser() {}

    public record SlackLink(String url, String label) {}

    // 같은 URL이 본문에 여러 번 나와도 처음 등장한 것만 남긴다(순서 유지)
    public static List<SlackLink> extract(String content) {
        List<SlackLink> links = new ArrayList<>();
        if (content == null) {
            return links;
        }
        List<String> seen = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group(1);
            if (seen.contains(url)) {
                continue;
            }
            seen.add(url);
            String label = matcher.group(2) != null ? matcher.group(2) : url;
            links.add(new SlackLink(url, label));
        }
        return links;
    }

    // 라벨이 구두점/공백뿐이면(예: "[", "]") 의미 있는 제목이 아니므로 - 실수로 걸린 대괄호 등을 걸러내는 데 사용
    public static boolean isJunkLabel(String label) {
        return label == null || label.trim().replaceAll("[\\p{Punct}\\s]", "").isEmpty();
    }
}
