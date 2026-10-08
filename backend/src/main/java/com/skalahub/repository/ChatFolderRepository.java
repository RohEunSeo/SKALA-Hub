// ChatFolder 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.ChatFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatFolderRepository extends JpaRepository<ChatFolder, Long> {

    List<ChatFolder> findByUser_SlackIdOrderBySortOrderAscIdAsc(String slackId);

    // 남의 폴더를 건드리지 못하게 id 와 소유자를 함께 본다
    Optional<ChatFolder> findByIdAndUser_SlackId(Long id, String slackId);

    boolean existsByUser_SlackIdAndName(String slackId, String name);

    long countByUser_SlackId(String slackId);
}
