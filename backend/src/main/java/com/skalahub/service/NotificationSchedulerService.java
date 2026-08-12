// 주간 TOP3 알림 스케줄러 - 매주 월요일, 방금 끝난 한 주 동안 반응 많은 글 TOP3 작성자에게 개인 알림 발송.
// SlackSyncService의 스케줄러(슬랙 동기화 전용)와는 책임이 달라(슬랙 API 호출 없음) 별도 클래스로 분리
package com.skalahub.service;

import com.skalahub.entity.Post;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public NotificationSchedulerService(
            PostRepository postRepository, UserRepository userRepository, NotificationService notificationService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${notification.weekly-top3-cron:0 0 9 * * MON}")
    public void computeWeeklyTop3() {
        try {
            LocalDate weekStart = LocalDate.now(ZONE).minusDays(7);
            LocalDateTime dateFrom = weekStart.atStartOfDay();

            List<Post> top3 = postRepository.findTopReactions(dateFrom);
            for (Post post : top3) {
                String authorSlackId = post.getUserSlackId();
                if (authorSlackId == null || !userRepository.existsById(authorSlackId)) {
                    continue; // 슬랙에서 수집만 되고 SKALA Hub 계정이 없는 작성자는 알림 대상에서 제외
                }
                notificationService.notifyWeeklyTop3(authorSlackId, post.getId());
            }
        } catch (Exception e) {
            log.error("주간 TOP3 알림 생성 실패", e);
        }
    }
}
