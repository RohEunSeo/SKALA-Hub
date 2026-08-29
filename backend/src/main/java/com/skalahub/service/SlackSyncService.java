// 슬랙 게시글/댓글 수집 (전체 동기화 - 관리자 API와 스케줄러가 공유)
package com.skalahub.service;

import com.skalahub.entity.Post;
import com.skalahub.entity.Reply;
import com.skalahub.entity.SyncFailure;
import com.skalahub.repository.PostRepository;
import com.skalahub.repository.ReplyRepository;
import com.skalahub.repository.SyncFailureRepository;
import com.skalahub.util.SlackLinkParser;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SlackSyncService {

    private static final Logger log = LoggerFactory.getLogger(SlackSyncService.class);

    // 슬랙 유저 멘션(<@U0123ABC>) 원문 패턴 - 프론트에서 렌더링할 수 있도록 이름을 붙여 <@U0123ABC|이름> 형태로 치환
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@([A-Z0-9]+)>");

    // 슬랙 채널 멘션(<#C0123ABC>) 원문 패턴 - 프론트에서 채널명으로 표시/링크할 수 있도록 <#C0123ABC|채널명> 형태로 치환
    private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#([A-Z0-9]+)>");

    // 시스템 알림 메시지는 게시글로 취급하지 않음
    private static final Set<String> SKIP_SUBTYPES = Set.of(
            "channel_join", "channel_leave", "channel_topic", "channel_purpose",
            "channel_name", "channel_archive", "channel_unarchive",
            "bot_add", "bot_remove", "pinned_item", "unpinned_item");

    // 전체 동기화 자체(fetchAllMessages)가 실패했을 때 sync_failures 테이블에 기록하는 고정 키 -
    // 개별 게시글 실패와 같은 목록에 같이 뜨지만 contentPreview로 구분 가능
    private static final String WHOLE_SYNC_ERROR_KEY = "__SYNC_ERROR__";

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final CategoryClassifier categoryClassifier;
    private final PostTitleService postTitleService;
    private final SlackBotReplyService slackBotReplyService;
    private final SlackDmNotificationService slackDmNotificationService;
    private final SyncFailureRepository syncFailureRepository;
    private final LinkPreviewFetchService linkPreviewFetchService;
    private final RestClient restClient = RestClient.create();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final String userToken;
    private final String channelId;
    private final int recentSyncWindowDays;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    // 유저 이름/프로필사진은 거의 안 바뀌므로 동기화 실행 간에도 재사용 - 매번 재조회하지 않아 API 호출량/소요시간 절감
    private final Map<String, SlackUserInfo> userInfoCache = new ConcurrentHashMap<>();

    // 채널명도 거의 안 바뀌므로 동기화 실행 간에도 재사용
    private final Map<String, String> channelNameCache = new ConcurrentHashMap<>();

    public SlackSyncService(
            PostRepository postRepository,
            ReplyRepository replyRepository,
            CategoryClassifier categoryClassifier,
            PostTitleService postTitleService,
            SlackBotReplyService slackBotReplyService,
            SlackDmNotificationService slackDmNotificationService,
            SyncFailureRepository syncFailureRepository,
            LinkPreviewFetchService linkPreviewFetchService,
            @Value("${slack.user-token}") String userToken,
            @Value("${slack.channel-id}") String channelId,
            @Value("${slack.sync-recent-window-days:7}") int recentSyncWindowDays) {
        this.postRepository = postRepository;
        this.replyRepository = replyRepository;
        this.categoryClassifier = categoryClassifier;
        this.postTitleService = postTitleService;
        this.slackBotReplyService = slackBotReplyService;
        this.slackDmNotificationService = slackDmNotificationService;
        this.syncFailureRepository = syncFailureRepository;
        this.linkPreviewFetchService = linkPreviewFetchService;
        this.userToken = userToken;
        this.channelId = channelId;
        this.recentSyncWindowDays = recentSyncWindowDays;
    }

    // 채널 전체를 매번 처음부터 재스캔하면 게시글이 쌓일수록 API 호출량/소요시간이 계속 늘어나므로,
    // 짧은 주기(5분)에는 "최근 N일 이내" 글만 훑어서 새 글 감지는 물론 최근 글의 반응/댓글수·수정사항도
    // 그대로 실시간에 가깝게 반영하고, 그보다 오래된 글까지 훑는 전체 재스캔은 하루 한 번(scheduledFullSync)만 수행
    @Scheduled(fixedDelayString = "${slack.sync-interval-ms:1800000}")
    public void scheduledSync() {
        try {
            incrementalSync();
        } catch (Exception e) {
            log.error("자동 동기화 실패", e);
        }
    }

    @Scheduled(cron = "${slack.full-sync-cron:0 0 4 * * *}")
    public void scheduledFullSync() {
        try {
            syncAll();
        } catch (Exception e) {
            log.error("전체 재동기화 실패", e);
        }
    }

    // 최근 N일 이내 글만 재조회 - 이 범위 안에서는 새 글/댓글/반응수/수정 모두 이전과 동일하게 5분마다 반영됨.
    // 그보다 오래된 글의 반응수 변화나 수정은 여기선 안 잡히고 하루 1회 전체 재동기화에서 잡힘.
    // 관리자 API(POST /api/admin/sync)의 기본 동기화도 이 메서드를 호출 - API 호출량이 적어 자주 눌러도 부담 없음
    public SyncSummary incrementalSync() {
        long cutoffEpochSeconds = Instant.now().minus(Duration.ofDays(recentSyncWindowDays)).getEpochSecond();
        return runSync(String.valueOf(cutoffEpochSeconds));
    }

    // 관리자 API(POST /api/admin/sync-full)와 하루 1회 전체 재동기화 스케줄러가 호출하는 전체 재스캔 진입점.
    // 채널 히스토리 전체를 다시 훑어 API 호출량이 많으므로 평소엔 필요 없고, 오래된 글의 반응수/수정 보정이나
    // 과거 데이터 일괄 복구가 필요할 때만 사용
    public SyncSummary syncAll() {
        return runSync(null);
    }

    // 관리자 모드 "동기화 실패 목록"에서 조회 - 최근 실패한 순서대로 반환
    public List<SyncFailure> getSyncFailures() {
        return syncFailureRepository.findAllByOrderByFailedAtDesc();
    }

    private void recordSyncFailure(String slackTs, JsonNode msg, Exception e) {
        String text = msg.path("text").asString("");
        String preview = text.length() > 120 ? text.substring(0, 120) + "..." : text;
        recordSyncFailure(slackTs, preview, e);
    }

    private void recordSyncFailure(String slackTs, String preview, Exception e) {
        syncFailureRepository.save(
                new SyncFailure(slackTs, preview, e.getMessage(), LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
    }

    // oldest가 있으면 그 시점 이후 메시지만, null이면 채널 히스토리 전체를 조회
    private SyncSummary runSync(String oldest) {
        if (!syncing.compareAndSet(false, true)) {
            throw new SyncAlreadyRunningException("이미 동기화가 진행 중입니다");
        }
        long start = System.currentTimeMillis();
        try {
            List<JsonNode> messages;
            try {
                messages = fetchAllMessages(oldest);
                syncFailureRepository.deleteBySlackTs(WHOLE_SYNC_ERROR_KEY);
            } catch (Exception e) {
                recordSyncFailure(WHOLE_SYNC_ERROR_KEY, "⚠️ 전체 동기화 자체 실패 (토큰 만료·슬랙 장애 등 의심)", e);
                throw e;
            }

            int newPosts = 0;
            int repliesProcessed = 0;
            for (JsonNode msg : messages) {
                String subtype = msg.path("subtype").asString("");
                if (SKIP_SUBTYPES.contains(subtype)) {
                    continue;
                }
                String slackTs = msg.path("ts").asString("");
                if (slackTs.isBlank()) {
                    continue;
                }

                Optional<Post> existing = postRepository.findBySlackTs(slackTs);
                boolean isNew = existing.isEmpty();
                // "동기화 중" 댓글을 따로 달았었는데, 저장+분류가 1~2초 안에 끝나서 완료 댓글과 거의
                // 동시에 뜨는 바람에 의미가 없었음 - 완료(또는 실패) 댓글 하나만 남김
                Post post;
                try {
                    post = upsertPost(existing.orElseGet(Post::new), isNew, msg, userInfoCache);
                    syncFailureRepository.deleteBySlackTs(slackTs);
                } catch (Exception e) {
                    log.error("게시글 저장 실패 (slackTs={})", slackTs, e);
                    recordSyncFailure(slackTs, msg, e);
                    String preview = msg.path("text").asString("");
                    slackDmNotificationService.sendSyncFailure(
                            slackTs, preview, e.getMessage(), postRepository.countByIsDeletedFalse());
                    continue;
                }
                if (isNew) {
                    newPosts++;
                    if (slackBotReplyService.isLocalFrontendUrl()) {
                        // 로컬 환경에서 동기화되면 배포 링크를 만들 수 없으므로 알림을 보류하고 표시만 해둠 -
                        // 관리자 모드 "대기" 목록에서 배포 환경 확인 후 수동으로 전송
                        post.setPendingNotification(true);
                        postRepository.save(post);
                    } else {
                        slackBotReplyService.notifySyncSuccess(slackTs, post.getId());
                        slackDmNotificationService.sendSyncResult(post, postRepository.countByIsDeletedFalse());
                    }
                }
                if (msg.path("reply_count").asInt(0) > 0) {
                    repliesProcessed += syncReplies(post, slackTs, userInfoCache);
                }
            }

            long durationMs = System.currentTimeMillis() - start;
            SyncSummary summary = new SyncSummary(messages.size(), newPosts, repliesProcessed, durationMs);
            log.info("슬랙 동기화 완료 (oldest={}): {}", oldest, summary);
            return summary;
        } finally {
            syncing.set(false);
        }
    }

    private List<JsonNode> fetchAllMessages(String oldest) {
        List<JsonNode> all = new ArrayList<>();
        String cursor = "";
        do {
            JsonNode page = callHistoryPage(cursor, oldest);
            page.path("messages").forEach(all::add);
            cursor = page.path("response_metadata").path("next_cursor").asString("");
        } while (!cursor.isBlank());
        return all;
    }

    private JsonNode callHistoryPage(String cursor, String oldest) {
        StringBuilder uri = new StringBuilder("https://slack.com/api/conversations.history?channel={channel}&limit=200");
        List<Object> uriVars = new ArrayList<>();
        uriVars.add(channelId);
        if (oldest != null && !oldest.isBlank()) {
            uri.append("&oldest={oldest}");
            uriVars.add(oldest);
        }
        if (!cursor.isBlank()) {
            uri.append("&cursor={cursor}");
            uriVars.add(cursor);
        }
        JsonNode response = getWithRetry(uri.toString(), uriVars.toArray());
        if (!response.path("ok").asBoolean(false)) {
            throw new IllegalStateException("conversations.history 실패: " + describeError(response));
        }
        return response;
    }

    // 화면에 보이는 댓글 수(post.replyCount)는 봇 댓글을 포함한 실제 댓글 총수 - 봇 댓글 제외는
    // 랭킹보드 집계 쪽(PostRepository.findTopComments)에서만 별도로 처리
    private int syncReplies(Post post, String threadTs, Map<String, SlackUserInfo> userInfoCache) {
        List<JsonNode> replies = fetchAllReplies(threadTs);
        int count = 0;
        // 0번째는 부모 게시글 자신이므로 1번부터 순회
        for (int i = 1; i < replies.size(); i++) {
            JsonNode reply = replies.get(i);
            String slackTs = reply.path("ts").asString("");
            if (slackTs.isBlank()) {
                continue;
            }
            String userId = reply.path("user").asString(null);
            Reply entity = replyRepository.findBySlackTs(slackTs).orElseGet(Reply::new);
            SlackUserInfo userInfo = resolveUserInfo(userId, userInfoCache);
            entity.setPost(post);
            entity.setSlackTs(slackTs);
            entity.setUserSlackId(userId);
            entity.setUserName(userInfo.name());
            entity.setUserAvatarUrl(userInfo.avatarUrl());
            entity.setContent(resolveChannelMentions(resolveMentions(reply.path("text").asString(""), userInfoCache)));
            entity.setCreatedAt(tsToLocalDateTime(slackTs));
            replyRepository.save(entity);
            count++;
        }
        if (post.getReplyCount() == null || post.getReplyCount() != count) {
            post.setReplyCount(count);
            postRepository.save(post);
        }
        return count;
    }

    private List<JsonNode> fetchAllReplies(String threadTs) {
        List<JsonNode> all = new ArrayList<>();
        String cursor = "";
        do {
            String uri = cursor.isBlank()
                    ? "https://slack.com/api/conversations.replies?channel={channel}&ts={ts}&limit=200"
                    : "https://slack.com/api/conversations.replies?channel={channel}&ts={ts}&limit=200&cursor={cursor}";
            JsonNode response = cursor.isBlank()
                    ? getWithRetry(uri, channelId, threadTs)
                    : getWithRetry(uri, channelId, threadTs, cursor);
            if (!response.path("ok").asBoolean(false)) {
                throw new IllegalStateException("conversations.replies 실패: " + describeError(response));
            }
            response.path("messages").forEach(all::add);
            cursor = response.path("response_metadata").path("next_cursor").asString("");
        } while (!cursor.isBlank());
        return all;
    }

    private Post upsertPost(Post post, boolean isNew, JsonNode msg, Map<String, SlackUserInfo> userInfoCache) {
        String slackTs = msg.path("ts").asString();
        String userId = msg.path("user").asString(null);
        SlackUserInfo userInfo = resolveUserInfo(userId, userInfoCache);
        String userName = userInfo.name();

        post.setSlackTs(slackTs);
        post.setUserSlackId(userId);
        post.setUserName(userName);
        post.setUserAvatarUrl(userInfo.avatarUrl());
        post.setIsInstructor(userName.contains("교수") || userName.contains("전임"));
        // 관리자가 본문을 수동으로 고친 게시글은 재동기화가 슬랙 원문으로 되돌리지 않도록 건너뜀
        if (!Boolean.TRUE.equals(post.getContentManuallyEdited())) {
            post.setContent(resolveChannelMentions(resolveMentions(msg.path("text").asString(""), userInfoCache)));
        }
        post.setReactionCount(sumReactions(msg));
        post.setReplyCount(msg.path("reply_count").asInt(0));
        post.setAttachments(toJsonOrNull(msg.path("attachments")));
        post.setFiles(toJsonOrNull(msg.path("files")));
        post.setReactedUserIds(extractReactedUserIds(msg));
        post.setIsEdited(!msg.path("edited").isMissingNode());
        post.setCreatedAt(tsToLocalDateTime(slackTs));
        post.setSyncedAt(LocalDateTime.now());
        if (isNew) {
            post.setIsDeleted(false);
            post.setIsPinned(false); // 관리자가 수동으로만 변경하는 값 - 신규 글에만 초기화
            post.setIsExcludedFromRanking(false); // 관리자가 수동으로만 변경하는 값 - 신규 글에만 초기화
        }

        post = postRepository.save(post);

        if (post.getCategory() == null || post.getCategory().isBlank()) {
            categoryClassifier.classify(post);
            post = postRepository.save(post);
        }

        // 게시글 원본은 이미 저장 완료 - AI 제목은 느릴 수 있어(외부 링크 fetch 포함) 백그라운드로 채움
        if (post.getAiTitle() == null) {
            postTitleService.generateTitleAsync(post.getId());
        }

        fetchMissingLinkPreviews(post.getContent(), msg.path("attachments"));
        return post;
    }

    // 본문 링크 중 슬랙이 이미 미리보기(attachments)를 만들어준 URL은 제외하고, 나머지만 og:title/image를
    // 새로 캐싱 - 링크 하나가 실패/지연되어도 동기화 전체가 멈추지 않도록 개별적으로 방어
    private void fetchMissingLinkPreviews(String content, JsonNode attachments) {
        List<SlackLinkParser.SlackLink> links = SlackLinkParser.extract(content);
        if (links.isEmpty()) {
            return;
        }
        Set<String> attachmentUrls = new LinkedHashSet<>();
        if (attachments != null && attachments.isArray()) {
            for (JsonNode attachment : attachments) {
                String url = attachment.path("from_url").asString(attachment.path("title_link").asString(null));
                if (url != null) {
                    attachmentUrls.add(url);
                }
            }
        }
        for (SlackLinkParser.SlackLink link : links) {
            if (attachmentUrls.contains(link.url())) {
                continue;
            }
            try {
                linkPreviewFetchService.ensureCached(link.url());
            } catch (Exception e) {
                log.debug("링크 미리보기 캐싱 중 예외 (무시하고 계속): {} ({})", link.url(), e.toString());
            }
        }
    }

    // attachments/files 배열이 비어있으면 null로 저장 (빈 배열 문자열 방지)
    private String toJsonOrNull(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
            return null;
        }
        return jsonMapper.writeValueAsString(arrayNode);
    }

    private int sumReactions(JsonNode msg) {
        int total = 0;
        for (JsonNode reaction : msg.path("reactions")) {
            total += reaction.path("count").asInt(0);
        }
        return total;
    }

    // 이모지 종류에 상관없이 이 게시글에 반응을 남긴 유저 id 목록 (중복 제거) - 마이페이지 "반응한 글" 조회용
    private List<String> extractReactedUserIds(JsonNode msg) {
        Set<String> userIds = new LinkedHashSet<>();
        for (JsonNode reaction : msg.path("reactions")) {
            for (JsonNode user : reaction.path("users")) {
                userIds.add(user.asString());
            }
        }
        return new ArrayList<>(userIds);
    }

    // <@U0123ABC> 형태의 원시 멘션을 <@U0123ABC|이름> 형태로 치환 (프론트가 이름으로 표시할 수 있도록)
    private String resolveMentions(String text, Map<String, SlackUserInfo> userInfoCache) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = USER_MENTION_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String userId = matcher.group(1);
            String name = resolveUserInfo(userId, userInfoCache).name();
            matcher.appendReplacement(result, Matcher.quoteReplacement("<@" + userId + "|" + name + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // <#C0123ABC> 형태의 원시 채널 멘션을 <#C0123ABC|채널명> 형태로 치환 (프론트가 채널명 링크로 표시할 수 있도록)
    private String resolveChannelMentions(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = CHANNEL_MENTION_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String channelId = matcher.group(1);
            String name = channelNameCache.computeIfAbsent(channelId, this::fetchChannelNameFromSlack);
            matcher.appendReplacement(result, Matcher.quoteReplacement("<#" + channelId + "|" + name + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // conversations.info 조회 실패(권한 부족 등) 시 채널ID를 그대로 이름 자리에 채워 프론트에서 최소한 링크는 뜨게 함
    private String fetchChannelNameFromSlack(String channelId) {
        try {
            JsonNode response = getWithRetry("https://slack.com/api/conversations.info?channel={id}", channelId);
            if (!response.path("ok").asBoolean(false)) {
                return channelId;
            }
            return response.path("channel").path("name").asString(channelId);
        } catch (Exception e) {
            log.debug("채널 정보 조회 실패 (channelId={})", channelId, e);
            return channelId;
        }
    }

    private SlackUserInfo resolveUserInfo(String userId, Map<String, SlackUserInfo> cache) {
        if (userId == null) {
            return new SlackUserInfo("알 수 없음", null);
        }
        return cache.computeIfAbsent(userId, this::fetchUserInfoFromSlack);
    }

    private SlackUserInfo fetchUserInfoFromSlack(String userId) {
        try {
            JsonNode response = getWithRetry("https://slack.com/api/users.info?user={id}", userId);
            if (!response.path("ok").asBoolean(false)) {
                return new SlackUserInfo(userId, null);
            }
            JsonNode user = response.path("user");
            JsonNode profile = user.path("profile");
            String displayName = profile.path("display_name").asString("");
            if (displayName.isBlank()) {
                displayName = profile.path("real_name").asString(user.path("real_name").asString(userId));
            }
            String avatarUrl = profile.path("image_192").asString(null);
            return new SlackUserInfo(displayName, avatarUrl);
        } catch (Exception e) {
            log.debug("유저 정보 조회 실패 (userId={})", userId, e);
            return new SlackUserInfo(userId, null);
        }
    }

    private record SlackUserInfo(String name, String avatarUrl) {
    }

    private LocalDateTime tsToLocalDateTime(String ts) {
        double epochSeconds = Double.parseDouble(ts);
        Instant instant = Instant.ofEpochMilli((long) (epochSeconds * 1000));
        return LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Seoul"));
    }

    // 슬랙 레이트리밋(429) 시 Retry-After만큼 대기 후 1회 재시도
    private JsonNode getWithRetry(String uri, Object... uriVars) {
        try {
            return requestUserToken(uri, uriVars);
        } catch (HttpClientErrorException.TooManyRequests e) {
            long waitSeconds = 1;
            String retryAfter = e.getResponseHeaders() != null
                    ? e.getResponseHeaders().getFirst("Retry-After")
                    : null;
            if (retryAfter != null) {
                try {
                    waitSeconds = Long.parseLong(retryAfter);
                } catch (NumberFormatException ignored) {
                    // 기본 1초 대기 유지
                }
            }
            log.warn("슬랙 레이트리밋 - {}초 대기 후 재시도", waitSeconds);
            try {
                Thread.sleep(waitSeconds * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return requestUserToken(uri, uriVars);
        }
    }

    private JsonNode requestUserToken(String uri, Object... uriVars) {
        return restClient.get()
                .uri(uri, uriVars)
                .header("Authorization", "Bearer " + userToken)
                .retrieve()
                .body(JsonNode.class);
    }

    private String describeError(JsonNode response) {
        return response != null ? response.path("error").asString("unknown") : "no response";
    }

    public record SyncSummary(int postsProcessed, int newPosts, int repliesProcessed, long durationMs) {
    }

    // "이미 동기화 중"과 그 외 IllegalStateException(슬랙 API 실패 등)을 구분하기 위한 전용 타입 -
    // AdminController가 이것만 409로, 나머지는 502로 응답하도록 분리
    public static class SyncAlreadyRunningException extends IllegalStateException {
        public SyncAlreadyRunningException(String message) {
            super(message);
        }
    }
}
