// AnnouncementRead 엔티티 DB 접근 - 공지 읽음 여부/안읽음 수 조회
package com.skalahub.repository;

import com.skalahub.entity.AnnouncementRead;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, Long> {

    List<AnnouncementRead> findByUserId(String userId);

    // 삭제되지 않은 공지 중 아직 이 유저가 읽지 않은 개수
    @Query(
            value = """
            SELECT count(*) FROM announcements a
            WHERE a.is_deleted = false
              AND NOT EXISTS (
                  SELECT 1 FROM announcement_reads r
                  WHERE r.announcement_id = a.id AND r.user_id = :userId
              )
            """,
            nativeQuery = true)
    long countUnreadForUser(@Param("userId") String userId);
}
