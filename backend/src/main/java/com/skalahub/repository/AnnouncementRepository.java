// Announcement 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Announcement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByIsDeletedFalseOrderByCreatedAtDesc();
}
