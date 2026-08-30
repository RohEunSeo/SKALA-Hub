// 대시보드 탭 집계 (로그인한 사용자면 누구나) - 나무 성장/게시글 기여도 히트맵/명예의 전당/신규 가입자 추이/반별 로그인 현황/카테고리 분포
package com.skalahub.service;

import com.skalahub.dto.DashboardCategoryDistDto;
import com.skalahub.dto.DashboardHallOfFameEntryDto;
import com.skalahub.dto.DashboardHeatmapDayDto;
import com.skalahub.dto.DashboardHeatmapStatsDto;
import com.skalahub.dto.DashboardLoginBarDto;
import com.skalahub.dto.DashboardSignupPointDto;
import com.skalahub.dto.DashboardSummaryResponse;
import com.skalahub.dto.DashboardTreeStageDto;
import com.skalahub.entity.Post;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String[] WEEKDAY_LABELS = {"일", "월", "화", "수", "목", "금", "토"};
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");

    // 나무 성장 단계 - 누적 게시글 수 기준, 대충 잡은 값이라 필요하면 이 목록만 조정하면 됨 (100단위)
    private static final List<TreeStage> TREE_STAGES = List.of(
            new TreeStage(0, "🌱", "새싹"),
            new TreeStage(100, "🌿", "줄기"),
            new TreeStage(200, "🪴", "어린 나무"),
            new TreeStage(300, "🌲", "자라는 나무"),
            new TreeStage(400, "🌳", "무성한 나무"));

    // 진행률 바 전체 기준값 - 마지막 단계(400) 다음의 "다 자람" 지점, 바 위 눈금은 이 값 기준 비율로 배치
    private static final long BAR_MAX = 500;

    // 명예의 전당 카테고리 표시 순서 - categories.js와 동일 순서 유지
    private static final List<String> CATEGORY_ORDER =
            List.of("개발 툴·환경", "학습자료", "자격증·취업", "교육생 서비스", "교수님", "기타");

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LocalDate courseStart;
    private final LocalDate courseEnd;

    public DashboardService(
            PostRepository postRepository,
            UserRepository userRepository,
            @Value("${app.cohort-start-date}") String courseStart,
            @Value("${app.cohort-end-date}") String courseEnd) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.courseStart = LocalDate.parse(courseStart);
        this.courseEnd = LocalDate.parse(courseEnd);
    }

    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now(ZONE);
        long totalPostCount = postRepository.countByIsDeletedFalse();
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(today, courseEnd));

        List<DashboardHeatmapDayDto> heatmap = buildHeatmap();
        DashboardHeatmapStatsDto heatmapStats = buildHeatmapStats(heatmap, today);
        DashboardTreeStageDto treeStage = buildTreeStage(totalPostCount);
        List<DashboardSignupPointDto> signupTrend = buildSignupTrend(today);
        List<DashboardLoginBarDto> loginByClass = buildLoginByClass();
        List<DashboardCategoryDistDto> categoryDist = buildCategoryDist();
        Map<String, List<DashboardHallOfFameEntryDto>> hallOfFame = buildHallOfFame();

        return new DashboardSummaryResponse(
                courseStart,
                courseEnd,
                daysLeft,
                totalPostCount,
                treeStage,
                heatmap,
                heatmapStats,
                signupTrend,
                loginByClass,
                categoryDist,
                hallOfFame);
    }

    // 교육 기간 전체(courseStart~courseEnd)를 다 그린다 - 미래 날짜는 빈 칸(레벨 0)으로 표시
    private List<DashboardHeatmapDayDto> buildHeatmap() {
        Map<LocalDate, Long> countByDate = postRepository.countByCreatedAtDate().stream()
                .collect(Collectors.toMap(
                        row -> toLocalDate(row[0]), row -> ((Number) row[1]).longValue()));

        List<DashboardHeatmapDayDto> heatmap = new ArrayList<>();
        for (LocalDate d = courseStart; !d.isAfter(courseEnd); d = d.plusDays(1)) {
            long count = countByDate.getOrDefault(d, 0L);
            heatmap.add(new DashboardHeatmapDayDto(d, count, heatLevel(count)));
        }
        return heatmap;
    }

    // 0개(활동 없음)/1~4개/5~9개/10~14개/15개+ - 깃허브 잔디처럼 5단계(0 + 4단계 초록)
    private int heatLevel(long count) {
        if (count <= 0) return 0;
        if (count <= 4) return 1;
        if (count <= 9) return 2;
        if (count <= 14) return 3;
        return 4;
    }

    // 미래 날짜(아직 오지 않은 빈 칸)는 평균/최고기록/연속기록 계산에서 제외
    private DashboardHeatmapStatsDto buildHeatmapStats(List<DashboardHeatmapDayDto> heatmap, LocalDate today) {
        List<DashboardHeatmapDayDto> pastDays =
                heatmap.stream().filter(d -> !d.date().isAfter(today)).toList();
        if (pastDays.isEmpty()) {
            return new DashboardHeatmapStatsDto("-", 0, 0, "-", 0);
        }

        DashboardHeatmapDayDto maxDay = pastDays.stream()
                .max(Comparator.comparingLong(DashboardHeatmapDayDto::count))
                .orElseThrow();
        double avgPerDay =
                pastDays.stream().mapToLong(DashboardHeatmapDayDto::count).average().orElse(0);

        double[] sums = new double[7];
        long[] counts = new long[7];
        for (DashboardHeatmapDayDto d : pastDays) {
            int idx = d.date().getDayOfWeek().getValue() % 7; // MONDAY=1..SUNDAY=7 -> 일=0
            sums[idx] += d.count();
            counts[idx]++;
        }
        int bestWeekdayIdx = 0;
        double bestAvg = -1;
        for (int i = 0; i < 7; i++) {
            double avg = counts[i] == 0 ? 0 : sums[i] / counts[i];
            if (avg > bestAvg) {
                bestAvg = avg;
                bestWeekdayIdx = i;
            }
        }

        long streakDays = computeStreak(pastDays);

        String maxDayLabel = maxDay.date().getMonthValue() + "월 " + maxDay.date().getDayOfMonth() + "일";
        return new DashboardHeatmapStatsDto(
                maxDayLabel, maxDay.count(), avgPerDay, WEEKDAY_LABELS[bestWeekdayIdx] + "요일", streakDays);
    }

    // 가장 최근에 게시글이 올라온 날부터 거슬러 올라가며 끊기지 않고 이어지는 일수
    private long computeStreak(List<DashboardHeatmapDayDto> pastDaysAsc) {
        long streak = 0;
        boolean started = false;
        for (int i = pastDaysAsc.size() - 1; i >= 0; i--) {
            DashboardHeatmapDayDto day = pastDaysAsc.get(i);
            if (!started) {
                if (day.count() <= 0) continue; // 오늘 아직 글이 없으면 어제 이전부터 스트릭 계산
                started = true;
            }
            if (day.count() <= 0) break;
            streak++;
        }
        return streak;
    }

    private DashboardTreeStageDto buildTreeStage(long totalPostCount) {
        TreeStage current = TREE_STAGES.get(0);
        Long nextThreshold = null;
        for (int i = 0; i < TREE_STAGES.size(); i++) {
            TreeStage stage = TREE_STAGES.get(i);
            if (totalPostCount >= stage.min()) {
                current = stage;
                nextThreshold = i + 1 < TREE_STAGES.size() ? (long) TREE_STAGES.get(i + 1).min() : null;
            }
        }
        // 진행률 바는 단계별이 아니라 0~BAR_MAX 전체 기준 - 바 아래 눈금(100/200/300/400/500)과 같은 스케일이어야 함
        double progressPct = Math.min(100, totalPostCount * 100.0 / BAR_MAX);
        return new DashboardTreeStageDto(
                current.emoji(), current.label(), totalPostCount, nextThreshold, progressPct, BAR_MAX);
    }

    private List<DashboardSignupPointDto> buildSignupTrend(LocalDate today) {
        Map<LocalDate, Long> countByDate = userRepository.countByCreatedAtDate().stream()
                .collect(Collectors.toMap(
                        row -> toLocalDate(row[0]), row -> ((Number) row[1]).longValue()));

        // 교육 시작일이 아니라 SKALA Hub에 실제로 첫 가입자가 들어온 날부터 그래프를 그림
        LocalDateTime earliestSignupAt = userRepository.findEarliestSignupAt();
        LocalDate rangeStart = earliestSignupAt != null ? earliestSignupAt.toLocalDate() : courseStart;
        LocalDate rangeEnd = today.isAfter(courseEnd) ? courseEnd : today;
        if (rangeStart.isAfter(rangeEnd)) rangeStart = rangeEnd;

        long cumulative = userRepository.countByCreatedAtBefore(rangeStart.atStartOfDay());
        List<DashboardSignupPointDto> trend = new ArrayList<>();
        for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
            long newCount = countByDate.getOrDefault(d, 0L);
            cumulative += newCount;
            trend.add(new DashboardSignupPointDto(d, newCount, cumulative));
        }
        return trend;
    }

    // role 값 -> 화면에 보여줄 한글 라벨 (staff=운영진, manager=매니저, professor=교수님), 이 순서대로 표시
    private static final Map<String, String> STAFF_ROLE_LABELS =
            Map.of("staff", "운영진", "manager", "매니저", "professor", "교수님");
    private static final List<String> STAFF_ROLE_ORDER = List.of("staff", "manager", "professor");

    private List<DashboardLoginBarDto> buildLoginByClass() {
        List<DashboardLoginBarDto> bars = userRepository.countByClassNum().stream()
                .map(row -> new DashboardLoginBarDto((String) row[0], ((Number) row[1]).longValue(), false))
                .sorted(Comparator.comparingInt(bar -> extractLeadingNumber(bar.label())))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Long> staffCounts = userRepository.countByStaffRole().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));
        for (String role : STAFF_ROLE_ORDER) {
            bars.add(new DashboardLoginBarDto(STAFF_ROLE_LABELS.get(role), staffCounts.getOrDefault(role, 0L), true));
        }
        return bars;
    }

    // "1반".."10반" 문자열 정렬이 사전식으로 꼬이지 않도록 앞자리 숫자만 뽑아 비교
    private int extractLeadingNumber(String label) {
        Matcher m = LEADING_DIGITS.matcher(label);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private List<DashboardCategoryDistDto> buildCategoryDist() {
        Map<String, Long> counts = postRepository.countByCategory().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<DashboardCategoryDistDto> dist = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            long count = counts.getOrDefault(category, 0L);
            double pct = total == 0 ? 0 : count * 100.0 / total;
            dist.add(new DashboardCategoryDistDto(category, count, pct));
        }
        return dist;
    }

    private Map<String, List<DashboardHallOfFameEntryDto>> buildHallOfFame() {
        Map<String, List<DashboardHallOfFameEntryDto>> hallOfFame = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            List<DashboardHallOfFameEntryDto> entries = postRepository.findTopReactionsByCategory(category).stream()
                    .map(this::toHallOfFameEntry)
                    .toList();
            hallOfFame.put(category, entries);
        }
        return hallOfFame;
    }

    private DashboardHallOfFameEntryDto toHallOfFameEntry(Post post) {
        return new DashboardHallOfFameEntryDto(
                post.getId(),
                post.getUserName(),
                post.getIsInstructor(),
                post.getContent(),
                post.getAiTitle(),
                post.getReactionCount(),
                post.getCreatedAt());
    }

    // Hibernate 버전/드라이버에 따라 native query의 DATE(...) 결과가 java.sql.Date로 오기도 하고
    // java.time.LocalDate로 바로 오기도 해서, 캐스팅 대신 타입을 보고 안전하게 변환
    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw new IllegalStateException("Unexpected date type: " + value.getClass());
    }

    private record TreeStage(int min, String emoji, String label) {
    }
}
