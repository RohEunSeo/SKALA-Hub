// Swagger(OpenAPI) 문서 기본 정보 설정
package com.skalahub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI skalaHubOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SKALA Hub API")
                .description("SKALA 부트캠프 슬랙 아카이빙 플랫폼 API 명세서")
                .version("v0.0.1"));
    }
}
