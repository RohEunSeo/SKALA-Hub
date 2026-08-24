// User 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, String> {

    // 대시보드 신규 가입자 추이 - 날짜별 신규 가입자 수
    @Query(
            value =
                    "SELECT DATE(created_at) AS d, count(*) FROM users WHERE created_at IS NOT NULL GROUP BY DATE(created_at)",
            nativeQuery = true)
    List<Object[]> countByCreatedAtDate();

    // 신규 가입자 추이 그래프의 시작일 이전(교육 시작일 이전 테스트 계정 등)에 이미 가입돼 있던 인원 수 - 누적치 기준점
    long countByCreatedAtBefore(LocalDateTime dateTime);

    // 대시보드 신규 가입자 추이 그래프 시작점 - 맨 처음 가입한 날짜(=SKALA Hub 오픈일)부터 보여주기 위한 기준
    @Query(value = "SELECT MIN(created_at) FROM users", nativeQuery = true)
    LocalDateTime findEarliestSignupAt();

    // 대시보드 반별 로그인(가입) 현황 - admin은 권한일 뿐 학생이므로 student와 함께 집계
    @Query(
            value =
                    "SELECT class_num, count(*) FROM users WHERE role IN ('student', 'admin') AND class_num IS NOT NULL GROUP BY class_num",
            nativeQuery = true)
    List<Object[]> countByClassNum();

    // 대시보드 스태프(운영진·매니저·교수님) 역할별 인원 수 - role 값: staff(운영진)/manager(매니저)/professor(교수님)
    @Query(
            value = "SELECT role, count(*) FROM users WHERE role IN ('professor', 'staff', 'manager') GROUP BY role",
            nativeQuery = true)
    List<Object[]> countByStaffRole();
}
