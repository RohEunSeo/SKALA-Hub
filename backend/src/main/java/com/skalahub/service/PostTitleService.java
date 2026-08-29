// AI 제목 생성 비동기 진입점 - 동기화/관리자 일괄 생성 양쪽에서 호출. 게시글 저장을 막지 않도록
// titleGenerationExecutor(최대 동시 3건) 위에서 별도 스레드로 실행됨
package com.skalahub.service;

import com.skalahub.repository.PostRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostTitleService {

    private final PostRepository postRepository;
    private final PostTitleGenerator postTitleGenerator;

    public PostTitleService(PostRepository postRepository, PostTitleGenerator postTitleGenerator) {
        this.postRepository = postRepository;
        this.postTitleGenerator = postTitleGenerator;
    }

    @Async("titleGenerationExecutor")
    @Transactional
    public void generateTitleAsync(Long postId) {
        postRepository.findById(postId).ifPresent(post -> {
            postTitleGenerator.generateTitle(post);
            postRepository.save(post);
        });
    }
}
