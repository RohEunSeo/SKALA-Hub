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

    private LocalDateTime createdAt;

    private LocalDateTime syncedAt;
}
