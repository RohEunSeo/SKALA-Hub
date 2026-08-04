// 저장하기(북마크) 저장/삭제/조회
package com.skalahub.service;

import com.skalahub.entity.Bookmark;
import com.skalahub.entity.Post;
import com.skalahub.entity.User;
import com.skalahub.repository.BookmarkRepository;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public BookmarkService(
            BookmarkRepository bookmarkRepository, UserRepository userRepository, PostRepository postRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    public List<Long> getBookmarkedPostIds(String slackId) {
        return bookmarkRepository.findByUser_SlackId(slackId).stream()
                .map(bookmark -> bookmark.getPost().getId())
                .toList();
    }

    @Transactional
    public void save(String slackId, Long postId) {
        if (bookmarkRepository.findByUser_SlackIdAndPost_Id(slackId, postId).isPresent()) {
            return; // 이미 저장된 경우 - idempotent
        }
        User user = userRepository
                .findById(slackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        Post post = postRepository
                .findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"));

        Bookmark bookmark = new Bookmark();
        bookmark.setUser(user);
        bookmark.setPost(post);
        bookmark.setSavedAt(LocalDateTime.now());
        bookmarkRepository.save(bookmark);

        post.setBookmarkCount(post.getBookmarkCount() == null ? 1 : post.getBookmarkCount() + 1);
        postRepository.save(post);
    }

    @Transactional
    public void remove(String slackId, Long postId) {
        if (bookmarkRepository.findByUser_SlackIdAndPost_Id(slackId, postId).isEmpty()) {
            return; // 이미 없는 경우 - idempotent
        }
        bookmarkRepository.deleteByUser_SlackIdAndPost_Id(slackId, postId);

        postRepository.findById(postId).ifPresent(post -> {
            post.setBookmarkCount(Math.max(0, (post.getBookmarkCount() == null ? 0 : post.getBookmarkCount()) - 1));
            postRepository.save(post);
        });
    }
}
