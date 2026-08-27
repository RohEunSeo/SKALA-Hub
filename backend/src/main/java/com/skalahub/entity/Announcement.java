// 관리자 전체 공지 (announcements 테이블)
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String badgeType;

    @Column(length = 200)
    private String title;

    private String content;

    @Column(length = 50)
    private String createdBy;

    private LocalDateTime createdAt;

    private Boolean isDeleted;

    // 클릭 시 이동할 경로 (예: /feed?tab=links) - 관리자가 프리셋 선택 또는 직접 입력
    @Column(length = 300)
    private String linkPath;

    // 링크 버튼에 표시할 커스텀 이름 (예: "daily-it-news 게시글 바로가기") - 비어있으면 프론트에서 "바로가기"로 표시
    @Column(length = 50)
    private String linkLabel;

    // 두 번째 링크 (선택) - 첫 번째와 마찬가지로 라벨+경로 한 쌍
    @Column(length = 50)
    private String linkLabel2;

    @Column(length = 300)
    private String linkPath2;

    // 수정된 적 있으면 값이 들어감 - 관리자 목록에 "(수정됨)" 표시용
    private LocalDateTime updatedAt;
}
