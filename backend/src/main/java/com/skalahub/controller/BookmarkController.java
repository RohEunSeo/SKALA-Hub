// 저장하기(북마크) API - 로그인 필요 (SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.service.BookmarkService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 현재 로그인한 유저가 저장한 게시글 id 목록
    @GetMapping
    public List<Long> getBookmarks(Principal principal) {
        return bookmarkService.getBookmarkedPostIds(principal.getName());
    }

    @PostMapping
    public void save(Principal principal, @RequestBody Map<String, Long> body) {
        bookmarkService.save(principal.getName(), body.get("postId"));
    }

    @DeleteMapping("/{postId}")
    public void remove(Principal principal, @PathVariable Long postId) {
        bookmarkService.remove(principal.getName(), postId);
    }
}
