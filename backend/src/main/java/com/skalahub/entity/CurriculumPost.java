// SKALA 커리큘럼 탭 - 게시글을 커리큘럼 단계별로 수동 큐레이션 (원본 posts는 절대 건드리지 않음)
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "curriculum_posts")
@Getter
@Setter
@NoArgsConstructor
public class CurriculumPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // post_id UNIQUE - 게시글 1개는 커리큘럼 슬롯 1개에만 속함
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(length = 30)
    private String stage;

    @Column(name = "sub_category", length = 30)
    private String subCategory;

    // 커리큘럼 탭에서만 숨김(원본 게시글은 그대로) - 관리자가 다시 켜면 복원됨
    private Boolean isExcluded = false;

    @Column(name = "added_by", length = 50)
    private String addedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
