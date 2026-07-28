// JWT 발급 (교육생 로그인 세션 유지용)
package com.skalahub.service;

import com.skalahub.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getSlackId())
                .claim("name", user.getName())
                .claim("cohort", user.getCohort())
                .claim("campus", user.getCampus())
                .claim("classNum", user.getClassNum())
                .claim("role", user.getRole())
                .claim("profileImg", user.getProfileImg())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // Authorization 헤더의 토큰 검증 및 클레임 추출
    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
