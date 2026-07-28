// Reply 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Reply;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    Optional<Reply> findBySlackTs(String slackTs);

    List<Reply> findByPost_IdOrderByCreatedAtAsc(Long postId);
}
