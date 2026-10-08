// 내 폴더 (chat_folders 테이블) - 저장한 글을 사용자가 직접 분류하는 묶음
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_folders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_slack_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_slack_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String name;

    // '#RRGGBB' - 프론트 stores/folders.js 의 FOLDER_COLORS 값을 그대로 저장한다
    @Column(nullable = false, length = 9)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
