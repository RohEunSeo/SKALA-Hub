// Notification 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndIsReadFalse(String userId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE notifications SET is_read = true WHERE user_id = :userId AND is_read = false", nativeQuery = true)
    void markAllReadForUser(@Param("userId") String userId);
}
