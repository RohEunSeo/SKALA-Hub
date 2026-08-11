// 미리보기(attachments)가 없는 본문 텍스트 링크의 og:title/og:image를 가져와 link_previews에 캐싱
package com.skalahub.service;

import com.skalahub.entity.LinkPreview;
import com.skalahub.repository.LinkPreviewRepository;
import java.time.LocalDateTime;
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

            String title = metaContent(doc, "og:title");
            if (title == null) {
                title = doc.title();
            }
            preview.setTitle(blankToNull(title));
            preview.setImageUrl(blankToNull(metaContent(doc, "og:image")));
            preview.setServiceName(blankToNull(metaContent(doc, "og:site_name")));
            preview.setFetchFailed(false);
        } catch (Exception e) {
            log.debug("링크 미리보기 fetch 실패: {} ({})", url, e.toString());
            preview.setFetchFailed(true);
        }

        linkPreviewRepository.save(preview);
    }

    private String metaContent(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        String content = el != null ? el.attr("content") : null;
        return blankToNull(content);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
