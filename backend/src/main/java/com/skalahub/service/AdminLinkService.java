// 관리자 링크 모음 관리 - 카드에 보이는 제목 수동 수정, 링크 숨김(URL 전역), 미리보기 캐시 재수집
package com.skalahub.service;

import com.skalahub.dto.AdminLinkUpdateRequest;
import com.skalahub.dto.LinkPreviewDto;
import com.skalahub.dto.PostResponse;
import com.skalahub.entity.LinkPreview;
import com.skalahub.entity.Post;
import com.skalahub.repository.LinkPreviewRepository;
import com.skalahub.repository.PostRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminLinkService {

    private final LinkPreviewRepository linkPreviewRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final LinkPreviewFetchService linkPreviewFetchService;

    public AdminLinkService(
            LinkPreviewRepository linkPreviewRepository,
            PostRepository postRepository,
            PostService postService,
            LinkPreviewFetchService linkPreviewFetchService) {
        this.linkPreviewRepository = linkPreviewRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.linkPreviewFetchService = linkPreviewFetchService;
    }

    @Transactional
    public void updateLink(AdminLinkUpdateRequest request) {
        LinkPreview preview = linkPreviewRepository.findById(request.url()).orElseGet(() -> {
            LinkPreview created = new LinkPreview();
            created.setUrl(request.url());
            created.setFetchedAt(LocalDateTime.now());
            // 자동 fetch가 한 번도 성공한 적 없는 새 행이라는 표시 - ensureCached는 existsById면
            // 아무것도 안 건드리므로 이후 자동 재수집 대상에서도 자연히 제외됨
            created.setFetchFailed(true);
            return created;
        });

        if (request.title() != null) {
            preview.setAdminTitle(request.title().isBlank() ? null : request.title().trim());
        }
        if (request.source() != null) {
            preview.setAdminSource(request.source().isBlank() ? null : request.source().trim());
        }
        if (request.creators() != null) {
            preview.setAdminCreators(request.creators().isBlank() ? null : request.creators().trim());
        }
        if (request.typeTag() != null) {
            preview.setAdminTypeTag(request.typeTag().isBlank() ? null : request.typeTag().trim());
        }
        if (request.emoji() != null) {
            preview.setAdminEmoji(request.emoji().isBlank() ? null : request.emoji().trim());
        }
        if (request.hidden() != null) {
            preview.setHidden(request.hidden());
        }
        linkPreviewRepository.save(preview);
    }

    // 슬랙을 다시 훑지 않고(전체 재수집과 달리 API 호출 없음) 이미 우리 DB에 있는 게시글 본문을 스캔해서
    // 아직 link_previews에 한 번도 저장된 적 없는 URL만 새로 fetch 시도 - link_previews 컬럼 문제 등으로
    // 조용히 실패했던 링크들을 게시글 재동기화 없이 가볍게 복구하는 용도.
    // 링크 하나당 외부 사이트 fetch(최대 몇 초)가 걸리므로 전체를 하나의 트랜잭션으로 묶지 않음 -
    // ensureCached() 호출마다 각자 짧게 커밋되게 둬서 DB 커넥션을 오래 붙잡지 않는다
    public int backfillPreviews() {
        int attempted = 0;
        for (Post post : postRepository.findAll()) {
            if (Boolean.TRUE.equals(post.getIsDeleted())) {
                continue;
            }
            PostResponse response = postService.toResponse(post);
            for (LinkPreviewDto link : response.links()) {
                String url = link.fromUrl();
                if (url != null && !linkPreviewRepository.existsById(url)) {
                    linkPreviewFetchService.ensureCached(url);
                    attempted++;
                }
            }
        }
        return attempted;
    }
}
