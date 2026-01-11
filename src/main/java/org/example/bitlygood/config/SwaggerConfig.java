package org.example.bitlygood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        log.info("SwaggerConfig: OpenAPI Bean 생성 중...");
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Bitly URL 단축 서비스 API")
                        .version("1.0.0")
                        .description("URL 단축 및 리다이렉션 서비스를 제공하는 REST API")
                        .contact(new Contact()
                                .name("Bitly Good")
                                .email("support@example.com")));
        log.info("SwaggerConfig: OpenAPI Bean 생성 완료");
        return openAPI;
    }
}
