// 슬랙 url_private 파일(이미지) 프록시 - 토큰은 백엔드에만 보관
package com.skalahub.controller;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class FileProxyController {

    // SSRF 방지 - 슬랙 파일 호스트만 허용
    private static final Set<String> ALLOWED_HOSTS = Set.of("files.slack.com");

    private final RestClient restClient = RestClient.create();
    private final String userToken;

    public FileProxyController(@Value("${slack.user-token}") String userToken) {
        this.userToken = userToken;
    }

    @GetMapping("/api/files/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (uri.getHost() == null || !ALLOWED_HOSTS.contains(uri.getHost())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ResponseEntity<byte[]> slackResponse = restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + userToken)
                .retrieve()
                .toEntity(byte[].class);

        MediaType contentType = slackResponse.getHeaders().getContentType();
        return ResponseEntity.ok()
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                .body(slackResponse.getBody());
    }
}
