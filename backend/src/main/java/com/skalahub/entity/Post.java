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

    @Column(length = 50)
    private String category;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags;

    private Integer reactionCount;

    private Integer replyCount;

    private Boolean isDeleted;

    private Boolean isPinned;

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
