// Security 설정 - /api/admin/**은 role=admin만, /api/posts/**·/api/bookmarks/**·/api/mypage/**·/api/files/**는 로그인 필요, 나머지(홈 요약/순위보드 등)는 전체 허용
package com.skalahub.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String frontendUrl;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, @Value("${app.frontend-url}") String frontendUrl) {
        this.jwtAuthFilter = jwtAuthFilter;
        // CORS Origin 매칭은 완전 일치라서, 환경변수 끝에 "/"가 붙어있으면(브라우저 Origin 헤더는
        // 절대 "/"로 안 끝남) 모든 요청이 조용히 CORS로 막힘 - 방어적으로 제거
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/bookmarks/**").authenticated()
                .requestMatchers("/api/mypage/**").authenticated()
                .requestMatchers("/api/posts/**").authenticated()
                .requestMatchers("/api/links/**").authenticated()
                .requestMatchers("/api/files/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 프론트엔드(별도 origin)에서의 API 호출 허용 - Authorization 헤더로 JWT 전달
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
