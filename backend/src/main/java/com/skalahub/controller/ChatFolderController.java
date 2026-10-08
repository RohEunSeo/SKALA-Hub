// 내 폴더 API - 로그인 필요 (SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.entity.Bookmark;
import com.skalahub.entity.ChatFolder;
import com.skalahub.repository.BookmarkRepository;
import com.skalahub.service.ChatFolderService;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folders")
public class ChatFolderController {

    private final ChatFolderService folderService;
    private final BookmarkRepository bookmarkRepository;

    public ChatFolderController(ChatFolderService folderService, BookmarkRepository bookmarkRepository) {
        this.folderService = folderService;
        this.bookmarkRepository = bookmarkRepository;
    }

    /**
     * 내 폴더 목록 + 어느 글이 어느 폴더에 있는지.
     * 프론트 stores/folders.js 가 쓰던 { folders, map } 모양 그대로 돌려준다.
     */
    @GetMapping
    public Map<String, Object> list(Principal principal) {
        String slackId = principal.getName();
        List<Map<String, Object>> folders = folderService.list(slackId).stream()
                .map(ChatFolderController::toMap)
                .toList();

        // { postId: folderId } - 폴더에 담긴 것만
        Map<String, Long> map = new LinkedHashMap<>();
        for (Bookmark b : bookmarkRepository.findByUser_SlackId(slackId)) {
            if (b.getFolder() != null) {
                map.put(String.valueOf(b.getPost().getId()), b.getFolder().getId());
            }
        }
        return Map.of("folders", folders, "map", map);
    }

    @PostMapping
    public Map<String, Object> create(Principal principal, @RequestBody Map<String, String> body) {
        return toMap(folderService.create(principal.getName(), body.get("name"), body.get("color")));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(
            Principal principal, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object order = body.get("sortOrder");
        return toMap(folderService.update(
                principal.getName(),
                id,
                (String) body.get("name"),
                (String) body.get("color"),
                order == null ? null : ((Number) order).intValue()));
    }

    @DeleteMapping("/{id}")
    public void delete(Principal principal, @PathVariable Long id) {
        folderService.delete(principal.getName(), id);
    }

    /** 저장한 글들을 폴더에 담기. folderId 가 없으면 '미분류'로 되돌린다. */
    @PostMapping("/assign")
    @SuppressWarnings("unchecked")
    public void assign(Principal principal, @RequestBody Map<String, Object> body) {
        List<Integer> raw = (List<Integer>) body.getOrDefault("postIds", List.of());
        List<Long> postIds = raw.stream().map(Integer::longValue).toList();
        Object folderId = body.get("folderId");
        folderService.assign(
                principal.getName(), postIds, folderId == null ? null : ((Number) folderId).longValue());
    }

    private static Map<String, Object> toMap(ChatFolder f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("name", f.getName());
        m.put("color", f.getColor());
        return m;
    }
}
