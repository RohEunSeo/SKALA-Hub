// 커리큘럼 탭 관리자 큐레이션 - 게시글 추가/카테고리 변경/제외 (원본 posts는 변경하지 않음)
package com.skalahub.service;

import com.skalahub.dto.AdminCurriculumUpsertRequest;
import com.skalahub.dto.CurriculumPostResponse;
import com.skalahub.dto.CurriculumStatusDto;
import com.skalahub.entity.CurriculumPost;
import com.skalahub.entity.Post;
import com.skalahub.repository.CurriculumPostRepository;
import com.skalahub.repository.PostRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCurriculumService {

    private final CurriculumPostRepository curriculumPostRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    public AdminCurriculumService(
            CurriculumPostRepository curriculumPostRepository,
            PostRepository postRepository,
            PostService postService) {
        this.curriculumPostRepository = curriculumPostRepository;
        this.postRepository = postRepository;
        this.postService = postService;
    }

    // 없으면 새로 등록, 이미 있으면 stage/subCategory만 갱신(카테고리 변경) - 제외 상태였어도 다시 켜짐
    @Transactional
    public CurriculumPostResponse addOrUpdate(AdminCurriculumUpsertRequest request, String adminSlackId) {
        Post post = postRepository
                .findById(request.postId())
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + request.postId()));

        CurriculumPost entry = curriculumPostRepository.findByPost_Id(request.postId()).orElseGet(() -> {
            CurriculumPost created = new CurriculumPost();
            created.setPost(post);
            created.setAddedBy(adminSlackId);
            created.setCreatedAt(LocalDateTime.now());
            return created;
        });
        entry.setStage(request.stage());
        entry.setSubCategory(request.subCategory());
        entry.setIsExcluded(false);
        entry.setUpdatedAt(LocalDateTime.now());
        CurriculumPost saved = curriculumPostRepository.save(entry);

        return new CurriculumPostResponse(
                postService.toResponse(saved.getPost()),
                saved.getStage(),
                saved.getSubCategory(),
                saved.getAddedBy(),
                saved.getCreatedAt());
    }

    // 커리큘럼 탭에서만 숨김/복원 - posts 테이블은 절대 건드리지 않음
    @Transactional
    public void setExcluded(Long postId, boolean excluded) {
        CurriculumPost entry = curriculumPostRepository
                .findByPost_Id(postId)
                .orElseThrow(() -> new IllegalArgumentException("커리큘럼에 등록되지 않은 게시글입니다: " + postId));
        entry.setIsExcluded(excluded);
        entry.setUpdatedAt(LocalDateTime.now());
        curriculumPostRepository.save(entry);
    }

    // 피드 탭에서 보이는 게시글들이 이미 커리큘럼에 등록됐는지 한 번에 조회 (퀵애드 아이콘 상태 표시용)
    @Transactional(readOnly = true)
    public List<CurriculumStatusDto> getStatusForPostIds(List<Long> postIds) {
        return curriculumPostRepository.findByPost_IdInAndIsExcludedFalse(postIds).stream()
                .map(entry -> new CurriculumStatusDto(entry.getPost().getId(), entry.getStage(), entry.getSubCategory()))
                .toList();
    }
}
