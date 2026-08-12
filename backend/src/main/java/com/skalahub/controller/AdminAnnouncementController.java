// 관리자 전용 공지 작성/삭제 API (role=admin만 접근 - SecurityConfig에서 강제)
package com.skalahub.controller;

import com.skalahub.dto.AnnouncementCreateRequest;
import com.skalahub.entity.Announcement;
import com.skalahub.service.AnnouncementService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<Announcement> list() {
        return announcementService.listForAdmin();
    }

    @PostMapping
    public Announcement create(Principal principal, @RequestBody AnnouncementCreateRequest request) {
        return announcementService.create(principal.getName(), request);
    }

    @PatchMapping("/{id}")
    public Announcement update(@PathVariable Long id, @RequestBody AnnouncementCreateRequest request) {
        return announcementService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        announcementService.delete(id);
    }
}
