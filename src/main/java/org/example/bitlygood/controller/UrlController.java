package org.example.bitlygood.controller;

import lombok.RequiredArgsConstructor;
import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.example.bitlygood.service.UrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    /**
     * 새로운 URL 단축 API (alias와 만료일 지원)
     */
    @PostMapping("/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(@RequestBody CreateUrlRequest request) {
        CreateUrlResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 기존 URL 단축 API (하위 호환성 유지)
     * 
     * @deprecated 새로운 createShortUrl(CreateUrlRequest) 사용을 권장
     */
    @PostMapping("/urls/simple")
    @Deprecated
    public ResponseEntity<String> createShortUrlSimple(@RequestBody String originalUrl) {
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, null, null);
        CreateUrlResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getShortCode());
    }

    @GetMapping("/{shortUrl}")
    public ResponseEntity<Void> redirect(@PathVariable String shortUrl) {
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
