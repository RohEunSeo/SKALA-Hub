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

    // 구글 계정 연동 (교육 종료 후 슬랙 로그인 불가 시 대체 로그인 수단) - 미연동이면 둘 다 null
    @Column(length = 50, unique = true)
    private String googleId;

    @Column(length = 255)
    private String googleEmail;

    private LocalDateTime createdAt;

    private LocalDateTime lastLogin;
}
