// 관리자 게시글 관리 - 카테고리/태그/핀 수동 수정, 미분류 게시글 조회/일괄 분류
package com.skalahub.service;

import com.skalahub.dto.AdminPostUpdateRequest;
import com.skalahub.dto.BotReplyResponse;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.entity.Post;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.ReplyRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPostService {

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final CategoryClassifier categoryClassifier;
    private final PostService postService;
    private final SlackBotReplyService slackBotReplyService;

    public AdminPostService(
            PostRepository postRepository,
            ReplyRepository replyRepository,
            CategoryClassifier categoryClassifier,
            PostService postService,
            SlackBotReplyService slackBotReplyService) {
        this.postRepository = postRepository;
        this.replyRepository = replyRepository;
        this.categoryClassifier = categoryClassifier;
        this.postService = postService;
        this.slackBotReplyService = slackBotReplyService;
    }

    public PostPageResponse getUncategorized(int page, int size) {
        return postService.wrapPage(postRepository.findUncategorized(PageRequest.of(page, size)));
    }

    @Transactional
    public PostResponse updatePost(Long id, AdminPostUpdateRequest request) {
        Post post = postRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"));

        if (request.category() != null) {
            post.setCategory(request.category());
        }
        if (request.tags() != null) {
            post.setTags(request.tags());
        }
        if (request.isPinned() != null) {
            post.setIsPinned(request.isPinned());
        }
        post = postRepository.save(post);
        return postService.toResponse(post);
    }

    // 미분류 게시글 전체를 Claude로 재분류 - 관리자가 수동으로 트리거
    @Transactional
    public int classifyAllUncategorized() {
        List<Post> uncategorized = postRepository.findAllUncategorized();
        for (Post post : uncategorized) {
            categoryClassifier.classify(post);
            postRepository.save(post);
        }
        return uncategorized.size();
    }

    // 슬랙 봇이 남긴 동기화 안내 댓글 목록 - 관리자 모드에서 슬랙 채널 대신 여기서 확인/관리
    @Transactional(readOnly = true)
    public List<BotReplyResponse> getBotReplies() {
        return replyRepository
                .findBotReplies(SlackBotReplyService.SYNC_SUCCESS_MARKER, SlackBotReplyService.SYNC_FAILURE_MARKER)
                .stream()
                .map(reply -> new BotReplyResponse(
                        reply.getId(),
                        reply.getSlackTs(),
                        reply.getContent(),
                        reply.getCreatedAt(),
                        reply.getPost().getId(),
                        preview(reply.getPost().getContent())))
                .toList();
    }

    // 봇 댓글을 슬랙에서 삭제하고, 로컬 DB의 replies·post.replyCount도 같이 맞춤 - 슬랙에서 직접 지우면
    // DB엔 그대로 남아 다음에 또 목록에 보이므로 반드시 같이 처리해야 함
    @Transactional
    public void deleteBotReply(String ts) {
        slackBotReplyService.deleteReply(ts);
        replyRepository.findBySlackTs(ts).ifPresent(reply -> {
            Post post = reply.getPost();
            replyRepository.delete(reply);
            if (post.getReplyCount() != null && post.getReplyCount() > 0) {
                post.setReplyCount(post.getReplyCount() - 1);
                postRepository.save(post);
            }
        });
    }

    // 봇 댓글 내용을 슬랙과 로컬 DB에 동시에 반영
    @Transactional
    public void updateBotReply(String ts, String content) {
        slackBotReplyService.updateReply(ts, content);
        replyRepository.findBySlackTs(ts).ifPresent(reply -> {
            reply.setContent(content);
            replyRepository.save(reply);
        });
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 60 ? content.substring(0, 60) + "..." : content;
    }
}
