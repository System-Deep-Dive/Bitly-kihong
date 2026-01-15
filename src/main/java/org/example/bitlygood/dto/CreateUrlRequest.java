package org.example.bitlygood.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "URL 단축 요청")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @Schema(description = "단축할 원본 URL", example = "https://www.example.com/very/long/url", required = true)
    private String originalUrl;

    @Schema(description = "사용자 지정 별칭 (선택사항)", example = "my-custom-alias")
    private String alias;

    @Schema(description = "만료일 (ISO 8601 형식, 선택사항)", example = "2024-12-31T23:59:59")
    private String expirationDate;
}
