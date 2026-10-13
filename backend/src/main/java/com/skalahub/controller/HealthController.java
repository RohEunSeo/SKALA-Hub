// DB 연결 상태 확인용 헬스체크 엔드포인트
package com.skalahub.controller;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // DB 연결이 끊긴 경우 503을 반환해야 Render/모니터링이 실제 장애로 인식한다 -
    // 항상 200을 반환하면 DB 장애가 나도 헬스체크는 "정상"으로 보임
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(Map.of("status", "ok", "db", "connected"));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "error", "db", "disconnected"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "error", "db", "disconnected"));
        }
    }
}
