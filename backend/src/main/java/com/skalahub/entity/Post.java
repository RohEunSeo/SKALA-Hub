// 슬랙 게시글 (posts 테이블)
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String slackTs;

    @Column(length = 50)
    private String userSlackId;

    @Column(length = 100)
    private String userName;

    @Column(length = 500)
    private String userAvatarUrl;

    private Boolean isInstructor;

    private String content;

    // 관리자가 본문을 수동으로 고친 게시글 - true면 슬랙 재동기화 때 원문으로 덮어쓰지 않음
    private Boolean contentManuallyEdited = false;

    @Column(length = 50)
    private String category;

    // AI가 생성한 한 줄 제목/요약. null이면 아직 생성 전(동기화 시 또는 관리자 일괄 생성으로 채워짐)
    @Column(name = "ai_title", length = 200)
    private String aiTitle;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags;

    private Integer reactionCount;

    private Integer replyCount;

    private Integer bookmarkCount = 0;

    // 동기화는 완료됐지만 FRONTEND_URL이 로컬 주소여서 슬랙 성공 알림을 보류한 상태 - true인 동안은
    // 관리자가 "지금 전송"을 눌러야만 슬랙에 안내 댓글이 달림
    private Boolean pendingNotification = false;

    private Boolean isDeleted;

    private Boolean isPinned;

    // 게시글 삭제 없이 "가장 반응이 많은 글" 순위보드에서만 제외
    private Boolean isExcludedFromRanking;

    // 슬랙에서 수정된 메시지인지 (슬랙 응답의 edited 필드 유무로 판단)
    private Boolean isEdited;

    // 슬랙 원본 attachments(링크 미리보기) raw JSON 문자열
    @JdbcTypeCode(SqlTypes.JSON)
    private String attachments;

    // 슬랙 원본 files(첨부파일/이미지) raw JSON 문자열
    @JdbcTypeCode(SqlTypes.JSON)
    private String files;

    // 이 게시글에 이모지 반응을 남긴 유저의 slackId 목록 (마이페이지 "반응한 글" 조회용)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> reactedUserIds;

    private LocalDateTime createdAt;

    private LocalDateTime syncedAt;
}
