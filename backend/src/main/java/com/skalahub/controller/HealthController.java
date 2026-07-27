// DB 연결 상태 확인용 헬스체크 엔드포인트
package com.skalahub.controller;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        try (Connection connection = dataSource.getConnection()) {
            boolean connected = connection.isValid(2);
            return Map.of("status", "ok", "db", connected ? "connected" : "disconnected");
        } catch (Exception e) {
            return Map.of("status", "ok", "db", "disconnected");
        }
    }
}
