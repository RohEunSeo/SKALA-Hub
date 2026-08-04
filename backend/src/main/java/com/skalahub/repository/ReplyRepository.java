// Reply 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Reply;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    Optional<Reply> findBySlackTs(String slackTs);

    List<Reply> findByPost_IdOrderByCreatedAtAsc(Long postId);

    // 봇 댓글 삭제 시 post.replyCount를 같이 감소시키려고 post를 즉시 함께 조회 - 트랜잭션 없이 호출돼도
    // (자기 클래스 내부 호출이라 @Transactional 프록시가 안 걸림) post 필드에 지연 로딩 예외 없이 접근 가능
    @Query("SELECT r FROM Reply r JOIN FETCH r.post WHERE r.slackTs = :slackTs")
    Optional<Reply> findBySlackTsWithPost(@Param("slackTs") String slackTs);

    // 관리자 모드 "슬랙 봇 댓글 관리"에서 조회 - 동기화 안내 문구가 포함된 댓글만 골라냄
    @Query(
            "SELECT r FROM Reply r "
                    + "WHERE r.content LIKE CONCAT('%', :successMarker, '%') "
                    + "OR r.content LIKE CONCAT('%', :failureMarker, '%') "
                    + "ORDER BY r.createdAt DESC")
    List<Reply> findBotReplies(@Param("successMarker") String successMarker, @Param("failureMarker") String failureMarker);
}
