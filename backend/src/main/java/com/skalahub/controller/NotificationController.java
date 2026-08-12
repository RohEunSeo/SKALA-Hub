// 개인 알림 조회/읽음 처리 API - 로그인 필요 (SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.NotificationDto;
import com.skalahub.dto.UnreadCountResponse;
import com.skalahub.service.AnnouncementService;
import com.skalahub.service.NotificationService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AnnouncementService announcementService;

    public NotificationController(NotificationService notificationService, AnnouncementService announcementService) {
        this.notificationService = notificationService;
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<NotificationDto> list(Principal principal) {
        return notificationService.listForUser(principal.getName());
    }

    @PostMapping("/read-all")
    public void markAllRead(Principal principal) {
        notificationService.markAllRead(principal.getName());
    }

    // 알림벨 뱃지 - 전체 공지 + 개인 알림 안읽음 수를 한 번에
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Principal principal) {
        String userId = principal.getName();
        return new UnreadCountResponse(
                announcementService.getUnreadCount(userId), notificationService.getUnreadCount(userId));
    }
}
