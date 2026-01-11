package org.example.bitlygood.controller;

import org.example.bitlygood.controller.annotation.ApiCreated;
import org.example.bitlygood.controller.annotation.ApiRedirect;
import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.example.bitlygood.service.UrlService;
import org.example.bitlygood.util.PathExclusionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "URL", description = "URL 단축 및 리다이렉션 API")
public class UrlController {

    private final UrlService urlService;

    @Operation(summary = "URL 단축 생성", description = "원본 URL을 단축 URL로 변환합니다. 사용자 지정 alias와 만료일을 설정할 수 있습니다.")
    @ApiCreated
    @PostMapping("/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(
            @Parameter(description = "URL 단축 요청 정보", required = true) @RequestBody CreateUrlRequest request) {
        CreateUrlResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "URL 단축 생성 (간단 버전)", description = "기존 URL 단축 API (하위 호환성 유지). 새로운 createShortUrl API 사용을 권장합니다.", deprecated = true)
    @ApiCreated
    @PostMapping("/urls/simple")
    @Deprecated
    public ResponseEntity<String> createShortUrlSimple(
            @Parameter(description = "단축할 원본 URL", required = true) @RequestBody String originalUrl) {
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, null, null);
        CreateUrlResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getShortCode());
    }

    @Operation(summary = "URL 리다이렉션", description = "단축 URL을 원본 URL로 리다이렉션합니다.")
    @ApiRedirect
    @GetMapping("/{shortUrl}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "단축 URL 코드", required = true, example = "1A2B3C") @PathVariable String shortUrl) {
        // Swagger 및 시스템 경로 제외
        if (PathExclusionUtil.isExcluded(shortUrl)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String originalUrl = urlService.getOriginalUrl(shortUrl);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", originalUrl)
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

}
