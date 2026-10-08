// 내 폴더 만들기/수정/삭제 + 저장한 글을 폴더에 담기
package com.skalahub.service;

import com.skalahub.entity.Bookmark;
import com.skalahub.entity.ChatFolder;
import com.skalahub.entity.User;
import com.skalahub.repository.BookmarkRepository;
import com.skalahub.repository.ChatFolderRepository;
import com.skalahub.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatFolderService {

    // 한 사람이 만들 수 있는 폴더 수. 프론트(stores/folders.js)의 MAX_FOLDERS와 같은 값이어야 한다.
    private static final int MAX_FOLDERS = 20;

    private final ChatFolderRepository folderRepository;
    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    public ChatFolderService(
            ChatFolderRepository folderRepository,
            BookmarkRepository bookmarkRepository,
            UserRepository userRepository) {
        this.folderRepository = folderRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
    }

    public List<ChatFolder> list(String slackId) {
        return folderRepository.findByUser_SlackIdOrderBySortOrderAscIdAsc(slackId);
    }

    @Transactional
    public ChatFolder create(String slackId, String name, String color) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "폴더 이름은 1~20자로 입력해주세요");
        }
        if (folderRepository.existsByUser_SlackIdAndName(slackId, trimmed)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 이름의 폴더가 이미 있습니다");
        }
        if (folderRepository.countByUser_SlackId(slackId) >= MAX_FOLDERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "폴더는 최대 " + MAX_FOLDERS + "개까지 만들 수 있습니다");
        }
        User user = userRepository
                .findById(slackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        ChatFolder folder = new ChatFolder();
        folder.setUser(user);
        folder.setName(trimmed);
        folder.setColor(color == null || color.isBlank() ? "#B2A9E3" : color);
        folder.setSortOrder((int) folderRepository.countByUser_SlackId(slackId));
        return folderRepository.save(folder);
    }

    @Transactional
    public ChatFolder update(String slackId, Long id, String name, String color, Integer sortOrder) {
        ChatFolder folder = mine(slackId, id);
        if (name != null && !name.isBlank()) {
            folder.setName(name.trim());
        }
        if (color != null && !color.isBlank()) {
            folder.setColor(color);
        }
        if (sortOrder != null) {
            folder.setSortOrder(sortOrder);
        }
        return folder;
    }

    /** 폴더만 지운다. 담겨 있던 글은 DB의 on delete set null 로 '미분류'가 된다. */
    @Transactional
    public void delete(String slackId, Long id) {
        folderRepository.delete(mine(slackId, id));
    }

    /**
     * 저장한 글들을 폴더에 담는다. folderId 가 null 이면 폴더에서 빼 '미분류'로 되돌린다.
     * 아직 저장 안 된 글은 건너뛴다 - 저장은 BookmarkService 가 먼저 해야 한다.
     */
    @Transactional
    public void assign(String slackId, List<Long> postIds, Long folderId) {
        ChatFolder folder = folderId == null ? null : mine(slackId, folderId);
        for (Long postId : postIds) {
            bookmarkRepository
                    .findByUser_SlackIdAndPost_Id(slackId, postId)
                    .ifPresent((Bookmark b) -> b.setFolder(folder));
        }
    }

    /** 남의 폴더에 손대지 못하게 소유자까지 확인한다. */
    private ChatFolder mine(String slackId, Long id) {
        return folderRepository
                .findByIdAndUser_SlackId(id, slackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다"));
    }
}
