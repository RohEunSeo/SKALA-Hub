// 개인 알림 조회/읽음 처리 - 생성은 notifyXxx 메서드로만 가능(북마크 저장/주간 TOP3), 관리자 수동 발송 없음
package com.skalahub.service;

import com.skalahub.dto.NotificationDto;
import com.skalahub.entity.Notification;
import com.skalahub.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationDto> listForUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationDto(
                        n.getId(), n.getType(), n.getTitle(), n.getPostId(), Boolean.TRUE.equals(n.getIsRead()),
                        n.getCreatedAt()))
                .toList();
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.markAllReadForUser(userId);
    }

    // 누군가 내 게시글을 저장했을 때 - BookmarkService.save()에서 호출
    @Transactional
    public void notifyBookmarkReceived(String authorSlackId, Long postId) {
        create(authorSlackId, "BOOKMARK_RECEIVED", "누군가 내 글을 저장했어요", postId);
    }

    // 이번 주 TOP3 게시글에 진입했을 때 - NotificationSchedulerService에서 호출
    @Transactional
    public void notifyWeeklyTop3(String authorSlackId, Long postId) {
        create(authorSlackId, "WEEKLY_TOP3", "내 글이 이번 주 TOP3에 진입했어요!", postId);
    }

    private void create(String userId, String type, String title, Long postId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setPostId(postId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
}
