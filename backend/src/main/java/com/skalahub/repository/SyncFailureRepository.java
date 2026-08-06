// SyncFailure 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.SyncFailure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SyncFailureRepository extends JpaRepository<SyncFailure, String> {

    List<SyncFailure> findAllByOrderByFailedAtDesc();

    // deleteById는 없는 행을 지우려 하면 예외를 던지는데, "실패였다가 성공"이 훨씬 흔한 경로라
    // 있으면 지우고 없으면 조용히 넘어가는 별도 삭제로 처리
    @Modifying
    @Transactional
    @Query("DELETE FROM SyncFailure f WHERE f.slackTs = :slackTs")
    void deleteBySlackTs(@Param("slackTs") String slackTs);
}
