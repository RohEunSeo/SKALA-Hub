// Authorization 헤더의 JWT를 검증해 SecurityContext에 role 권한 설정
package com.skalahub.config;

import com.skalahub.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = jwtService.parseToken(token);
                String role = claims.get("role", String.class);
                if (role != null) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    var authentication =
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // 토큰이 없거나 유효하지 않으면 인증 없이 통과 (permitAll 라우트는 영향 없음)
            }
        }
        filterChain.doFilter(request, response);
    }

    // 대부분은 Authorization 헤더로 오지만, <img src>처럼 브라우저가 헤더를 못 붙이는
    // 요청(파일 프록시)을 위해 쿼리 파라미터(token)도 보조 수단으로 허용
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String queryToken = request.getParameter("token");
        return (queryToken != null && !queryToken.isBlank()) ? queryToken : null;
    }
}
