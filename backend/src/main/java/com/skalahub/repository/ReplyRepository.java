// Reply 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Reply;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyRepository extends JpaRepository<Reply, Long> {
}
