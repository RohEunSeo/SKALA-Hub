// 미리보기(attachments)가 없는 본문 텍스트 링크의 og:title/og:image를 가져와 link_previews에 캐싱
package com.skalahub.service;

import com.skalahub.entity.LinkPreview;
import com.skalahub.repository.LinkPreviewRepository;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkPreviewFetchService {

    private static final Logger log = LoggerFactory.getLogger(LinkPreviewFetchService.class);
    private static final int TIMEOUT_MS = 4000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; SkalaHubBot/1.0)";

    // naver.me(네이버 지도 단축링크)는 리다이렉트된 map.naver.com 페이지가 클라이언트 사이드 렌더링이라
    // og:title/<title>이 항상 비어있음 - 대신 모바일 플레이스 페이지(m.place.naver.com)는 서버에서
    // 렌더링돼 내려오는데, 그 안에 박힌 JSON에 상호명이 "name":"..."으로 들어있어 이걸 대신 긁어옴.
    // 비공식 페이지 구조를 파싱하는 방식이라 네이버가 구조를 바꾸면 조용히 실패할 수 있음(실패해도
    // 그냥 제목 없이 저장되고 관리자가 링크 모음에서 수동으로 채울 수 있어 안전)
    private static final Pattern NAVER_PLACE_ID = Pattern.compile("place/(\\d+)");
    private static final Pattern NAVER_PLACE_NAME = Pattern.compile("\"name\":\"([^\"]{1,60})\"");
    private static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15";

    private final LinkPreviewRepository linkPreviewRepository;

    public LinkPreviewFetchService(LinkPreviewRepository linkPreviewRepository) {
        this.linkPreviewRepository = linkPreviewRepository;
    }

    // 이미 캐시(성공/실패 무관)되어 있으면 아무것도 하지 않음 - 실패한 URL을 매 동기화마다 재요청하지 않기 위함
    public void ensureCached(String url) {
        if (linkPreviewRepository.existsById(url)) {
            return;
        }

        LinkPreview preview = new LinkPreview();
        preview.setUrl(url);
        preview.setFetchedAt(LocalDateTime.now());

        try {
            // Jsoup.connect().get()은 응답 Content-Type 헤더에 charset이 없어도(꽤 흔함) <meta charset> 태그를
            // 보고 인코딩을 스스로 판별해준다 - RestClient로 문자열만 받으면 헤더에 charset이 없을 때
            // 기본값(ISO-8859-1)으로 잘못 디코딩되어 한글 제목이 깨지는 문제가 있었음
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();

            boolean isNaverMap = doc.location() != null && doc.location().contains("map.naver.com");

            String title = metaContent(doc, "og:title");
            if (title == null) {
                title = doc.title();
            }
            if ((title == null || title.isBlank()) && isNaverMap) {
                title = fetchNaverPlaceName(doc.location());
            }
            preview.setTitle(blankToNull(title));
            // 네이버 지도 페이지는 실제 장소 사진이 아니라 "네이버 지도" 고정 기본 로고 이미지를
            // og:image로 내려줘서, 그걸 그대로 쓰면 카드 썸네일이 전부 같은 지도 아이콘으로 보임 -
            // 비워둬서 프론트가 이모지(admin_emoji 또는 기본값)로 대체 표시하게 함
            preview.setImageUrl(isNaverMap ? null : toHttps(blankToNull(metaContent(doc, "og:image"))));
            preview.setServiceName(blankToNull(metaContent(doc, "og:site_name")));
            preview.setFetchFailed(false);
        } catch (Exception e) {
            log.debug("링크 미리보기 fetch 실패: {} ({})", url, e.toString());
            preview.setFetchFailed(true);
        }

        linkPreviewRepository.save(preview);
    }

    // map.naver.com 플레이스 URL(예: .../place/13499703?...)에서 id를 뽑아 모바일 페이지를 대신 긁어서
    // 상호명을 찾는다 - 실패해도 예외를 삼키고 null만 반환(제목 없이 저장되고 관리자가 나중에 채울 수 있음)
    private String fetchNaverPlaceName(String mapUrl) {
        Matcher idMatcher = NAVER_PLACE_ID.matcher(mapUrl);
        if (!idMatcher.find()) {
            return null;
        }
        try {
            String body = Jsoup.connect("https://m.place.naver.com/place/" + idMatcher.group(1) + "/home")
                    .userAgent(MOBILE_USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute()
                    .body();
            Matcher nameMatcher = NAVER_PLACE_NAME.matcher(body);
            return nameMatcher.find() ? nameMatcher.group(1) : null;
        } catch (Exception e) {
            log.debug("네이버 플레이스 이름 fetch 실패: {} ({})", mapUrl, e.toString());
            return null;
        }
    }

    private String metaContent(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        String content = el != null ? el.attr("content") : null;
        return blankToNull(content);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // http 이미지를 그대로 내려주면 프론트(https)에서 Mixed Content 경고가 뜸 - 대부분의 사이트가
    // https도 함께 지원하므로 스킴만 바꿔서 저장(진짜 https를 못 받으면 브라우저가 자동 업그레이드했을 때와
    // 동일하게 이미지 로드만 실패하고, 콘솔 경고는 사라짐)
    private String toHttps(String url) {
        if (url == null || !url.startsWith("http://")) {
            return url;
        }
        return "https://" + url.substring("http://".length());
    }
}
