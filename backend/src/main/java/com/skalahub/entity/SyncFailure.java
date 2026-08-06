// 동기화 실패 기록 (sync_failures 테이블) - slackTs가 자연키라 재실패해도 한 행만 갱신됨
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sync_failures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncFailure {

    @Id
    @Column(length = 50)
    private String slackTs;

    private String contentPreview;

    private String errorMessage;

    private LocalDateTime failedAt;
}
