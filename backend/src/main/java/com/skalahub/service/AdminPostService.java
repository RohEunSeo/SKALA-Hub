// 관리자 게시글 관리 - 카테고리/태그/핀/본문 수동 수정, 소프트 삭제, 미분류 게시글 조회/일괄 분류
package com.skalahub.service;

import com.skalahub.dto.AdminPostUpdateRequest;
import com.skalahub.dto.BotReplyResponse;
import com.skalahub.dto.PostPageResponse;
import com.skalahub.dto.PostResponse;
import com.skalahub.entity.Post;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.ReplyRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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

    // 순위보드에서 제외된 게시글 목록 - 홈 화면 관리자 전용 "제외된 글 보기" 패널에서 사용
    @Transactional(readOnly = true)
    public List<PostResponse> getExcludedFromRanking() {
        return postService.toResponses(postRepository.findByIsExcludedFromRankingTrueAndIsDeletedFalseOrderByReactionCountDesc());
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
        if (request.isExcludedFromRanking() != null) {
            post.setIsExcludedFromRanking(request.isExcludedFromRanking());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.isDeleted() != null) {
            post.setIsDeleted(request.isDeleted());
        }
        post = postRepository.save(post);
        return postService.toResponse(post);
    }

    // 미분류 게시글 전체를 Claude로 재분류 - 관리자가 수동으로 트리거. 하나씩 try-catch로 감싸서
    // 특정 글에서 Claude API 호출이 실패해도 나머지 글은 계속 처리되게 함 (실패한 글은 category가
    // 계속 비어있어서 다음 "일괄 분류" 클릭 때 자동으로 재시도 대상에 포함됨)
    @Transactional
    public ClassifyResult classifyAllUncategorized() {
        List<Post> uncategorized = postRepository.findAllUncategorized();
        int classified = 0;
        int failed = 0;
        for (Post post : uncategorized) {
            try {
                categoryClassifier.classify(post);
                postRepository.save(post);
                // classify()는 Claude API 실패 시에도 예외를 던지지 않고 category를 비워둔 채로
                // 조용히 반환하므로(다음 재시도를 위해), 실제 성공 여부는 category가 채워졌는지로 판단
                if (post.getCategory() != null && !post.getCategory().isBlank()) {
                    classified++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        return new ClassifyResult(classified, failed);
    }

    public record ClassifyResult(int classified, int failed) {
    }

    // 슬랙 봇이 남긴 동기화 안내 댓글 + 알림이 보류된(pending) 게시글을 합쳐서 반환 - 관리자 모드에서
    // 슬랙 채널 대신 여기서 확인/관리. pending은 아직 실제 슬랙 댓글이 없어서 id·ts가 null
    @Transactional(readOnly = true)
    public List<BotReplyResponse> getBotReplies() {
        Stream<BotReplyResponse> fromReplies = replyRepository
                .findBotReplies(SlackBotReplyService.SYNC_SUCCESS_MARKER, SlackBotReplyService.SYNC_FAILURE_MARKER)
                .stream()
                .map(reply -> new BotReplyResponse(
                        reply.getId(),
                        reply.getSlackTs(),
                        reply.getContent(),
                        reply.getCreatedAt(),
                        reply.getPost().getId(),
                        reply.getPost().getUserName(),
                        preview(reply.getPost().getContent()),
                        reply.getContent().contains(SlackBotReplyService.SYNC_SUCCESS_MARKER) ? "success" : "failure"));

        Stream<BotReplyResponse> pending = postRepository.findByPendingNotificationTrue().stream()
                .map(post -> new BotReplyResponse(
                        null,
                        null,
                        "⏳ 동기화는 완료됐지만 로컬 환경이라 배포 링크를 만들 수 없어 알림이 보류됐습니다.",
                        post.getSyncedAt(),
                        post.getId(),
                        post.getUserName(),
                        preview(post.getContent()),
                        "pending"));

        return Stream.concat(fromReplies, pending)
                .sorted(Comparator.comparing(BotReplyResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    // 배포 환경에서 관리자가 "지금 전송"을 눌렀을 때 - 여전히 로컬 주소면 또 같은 사고가 나므로 다시 한번 확인
    public void sendPendingNotification(Long postId) {
        Post post = postRepository
                .findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"));
        if (slackBotReplyService.isLocalFrontendUrl()) {
            throw new IllegalStateException("아직 FRONTEND_URL이 로컬 주소입니다 - 배포 환경에서 다시 시도해주세요");
        }
        slackBotReplyService.notifySyncSuccess(post.getSlackTs(), post.getId());
        clearPendingNotification(postId);
    }

    @Transactional
    public void clearPendingNotification(Long postId) {
        postRepository.findById(postId).ifPresent(post -> {
            post.setPendingNotification(false);
            postRepository.save(post);
        });
    }

    // 봇 댓글을 슬랙에서 삭제하고, 로컬 DB의 replies·post.replyCount도 같이 맞춤 - 슬랙에서 직접 지우면
    // DB엔 그대로 남아 다음에 또 목록에 보이므로 반드시 같이 처리해야 함
    // 슬랙 API 호출(느린 외부 네트워크 요청)은 트랜잭션 밖에서 먼저 끝내고, DB 반영은 그 뒤에 짧게 처리한다 -
    // 하나의 트랜잭션 안에 묶으면 외부 호출 동안 DB 커넥션을 오래 붙잡고 있다가, 커넥션이 끊기거나 타임아웃돼서
    // 슬랙은 이미 지워졌는데 로컬 DB 반영만 실패하는 문제가 있었음
    public void deleteBotReply(String ts) {
        try {
            slackBotReplyService.deleteReply(ts);
        } catch (IllegalStateException e) {
            // 이미 슬랙에서 지워진 메시지(message_not_found)면 실패로 보지 않고 로컬 정리를 계속 진행 -
            // 관리자가 슬랙에서 직접 지웠거나 이전 시도에서 슬랙만 지워지고 로컬 반영이 안 됐던 경우를 복구
            if (!e.getMessage().contains("message_not_found")) {
                throw e;
            }
        }
        removeLocalReply(ts);
    }

    @Transactional
    public void removeLocalReply(String ts) {
        replyRepository.findBySlackTsWithPost(ts).ifPresent(reply -> {
            Post post = reply.getPost();
            replyRepository.delete(reply);
            if (post.getReplyCount() != null && post.getReplyCount() > 0) {
                post.setReplyCount(post.getReplyCount() - 1);
                postRepository.save(post);
            }
        });
    }

    // 봇 댓글 내용을 슬랙과 로컬 DB에 반영 - 위와 같은 이유로 슬랙 호출과 DB 반영을 분리
    public void updateBotReply(String ts, String content) {
        slackBotReplyService.updateReply(ts, content);
        updateLocalReplyContent(ts, content);
    }

    @Transactional
    public void updateLocalReplyContent(String ts, String content) {
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
