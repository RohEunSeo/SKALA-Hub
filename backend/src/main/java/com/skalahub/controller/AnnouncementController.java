// 전체 공지 조회 API - 로그인 필요 (SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.AnnouncementDto;
import com.skalahub.service.AnnouncementService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<AnnouncementDto> list(Principal principal) {
        return announcementService.listForUser(principal.getName());
    }

    @PostMapping("/read-all")
    public void markAllRead(Principal principal) {
        announcementService.markAllRead(principal.getName());
    }
}
