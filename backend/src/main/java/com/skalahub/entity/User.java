// 교육생 계정 (users 테이블)
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(length = 50)
    private String slackId;

    @Column(length = 100)
    private String name;

    @Column(length = 10)
    private String cohort;

    @Column(length = 20)
    private String campus;

    @Column(length = 10)
    private String classNum;

    @Column(length = 20)
    private String role;

    @Column(length = 500)
    private String profileImg;

    private LocalDateTime createdAt;

    private LocalDateTime lastLogin;
}
