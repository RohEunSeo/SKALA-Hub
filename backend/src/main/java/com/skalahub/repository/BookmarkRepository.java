// Bookmark 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Bookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUser_SlackIdAndPost_Id(String slackId, Long postId);

    List<Bookmark> findByUser_SlackId(String slackId);

    Page<Bookmark> findByUser_SlackIdOrderBySavedAtDesc(String slackId, Pageable pageable);

    long countByUser_SlackId(String slackId);

    void deleteByUser_SlackIdAndPost_Id(String slackId, Long postId);
}
