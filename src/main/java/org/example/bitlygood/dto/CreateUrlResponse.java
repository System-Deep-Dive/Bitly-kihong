package org.example.bitlygood.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "URL 단축 응답")
@Getter
@AllArgsConstructor
public class CreateUrlResponse {

    @Schema(description = "생성된 단축코드", example = "1A2B3C")
    private String shortCode;

    @Schema(description = "전체 단축 URL", example = "http://localhost:8080/1A2B3C")
    private String shortUrl;

    @Schema(description = "원본 URL", example = "https://www.example.com/very/long/url")
    private String originalUrl;

    @Schema(description = "만료일", example = "2024-12-31T23:59:59")
    private String expirationDate;
}
