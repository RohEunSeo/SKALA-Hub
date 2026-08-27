// 전체 공지 작성/조회/읽음 처리 - 작성/삭제는 관리자만, 조회/읽음 처리는 로그인한 모든 유저
package com.skalahub.service;

import com.skalahub.dto.AnnouncementCreateRequest;
import com.skalahub.dto.AnnouncementDto;
import com.skalahub.entity.Announcement;
import com.skalahub.entity.AnnouncementRead;
import com.skalahub.repository.AnnouncementReadRepository;
import com.skalahub.repository.AnnouncementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;

    public AnnouncementService(
            AnnouncementRepository announcementRepository, AnnouncementReadRepository announcementReadRepository) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
    }

    @Transactional
    public Announcement create(String creatorSlackId, AnnouncementCreateRequest request) {
        Announcement announcement = new Announcement();
        announcement.setBadgeType(request.badgeType() == null || request.badgeType().isBlank() ? "공지" : request.badgeType());
        announcement.setTitle(request.title());
        announcement.setContent(request.content());
        announcement.setLinkLabel(blankToNull(request.linkLabel()));
        announcement.setLinkPath(blankToNull(request.linkPath()));
        announcement.setLinkLabel2(blankToNull(request.linkLabel2()));
        announcement.setLinkPath2(blankToNull(request.linkPath2()));
        announcement.setCreatedBy(creatorSlackId);
        announcement.setCreatedAt(LocalDateTime.now());
        announcement.setIsDeleted(false);
        return announcementRepository.save(announcement);
    }

    // 공지 수정 - createdAt(목록 정렬 기준)과 announcement_reads(읽음 상태)는 건드리지 않아,
    // 이미 읽은 유저에게 다시 안읽음으로 뜨지 않고 목록 순서도 유지됨
    @Transactional
    public Announcement update(Long id, AnnouncementCreateRequest request) {
        Announcement announcement = announcementRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다"));
        announcement.setBadgeType(request.badgeType() == null || request.badgeType().isBlank() ? "공지" : request.badgeType());
        announcement.setTitle(request.title());
        announcement.setContent(request.content());
        announcement.setLinkLabel(blankToNull(request.linkLabel()));
        announcement.setLinkPath(blankToNull(request.linkPath()));
        announcement.setLinkLabel2(blankToNull(request.linkLabel2()));
        announcement.setLinkPath2(blankToNull(request.linkPath2()));
        announcement.setUpdatedAt(LocalDateTime.now());
        return announcement;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // 관리자 모드 공지 목록 (읽음 여부 무관)
    public List<Announcement> listForAdmin() {
        return announcementRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
    }

    @Transactional
    public void delete(Long id) {
        Announcement announcement = announcementRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다"));
        announcement.setIsDeleted(true);
    }

    // 알림벨 "전체 공지" 탭 - 현재 유저의 읽음 여부를 함께 담아 반환
    public List<AnnouncementDto> listForUser(String userId) {
        Set<Long> readIds = announcementReadRepository.findByUserId(userId).stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toSet());

        return announcementRepository.findByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(a -> new AnnouncementDto(
                        a.getId(), a.getBadgeType(), a.getTitle(), a.getContent(),
                        a.getLinkLabel(), a.getLinkPath(), a.getLinkLabel2(), a.getLinkPath2(),
                        a.getCreatedAt(), a.getUpdatedAt(), readIds.contains(a.getId())))
                .toList();
    }

    public long getUnreadCount(String userId) {
        return announcementReadRepository.countUnreadForUser(userId);
    }

    @Transactional
    public void markAllRead(String userId) {
        Set<Long> readIds = announcementReadRepository.findByUserId(userId).stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toSet());

        List<Announcement> unread = announcementRepository.findByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(a -> !readIds.contains(a.getId()))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        for (Announcement announcement : unread) {
            AnnouncementRead read = new AnnouncementRead();
            read.setAnnouncementId(announcement.getId());
            read.setUserId(userId);
            read.setReadAt(now);
            announcementReadRepository.save(read);
        }
    }
}
