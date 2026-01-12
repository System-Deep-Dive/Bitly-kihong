package org.example.bitlygood.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.example.bitlygood.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * URL 단축 서비스
 * 
 * 이 서비스는 URL 단축 서비스의 핵심 비즈니스 로직을 담당합니다.
 * Redis 전역 카운터와 Base62 인코딩을 사용하여 고유하고 짧은 단축 코드를 생성합니다.
 * 
 * 주요 기능:
 * - URL 단축: 긴 URL을 짧은 코드로 변환
 * - URL 리다이렉션: 단축 코드로 원본 URL 조회
 * 
 * 아키텍처 특징:
 * - Redis 전역 카운터: 분산 환경에서도 고유한 ID 보장
 * - Base62 인코딩: URL-safe한 짧은 코드 생성
 * - 트랜잭션 관리: 데이터 일관성 보장
 * 
 * 동작 프로세스 (URL 단축):
 * 1. 신규 URL 등록 요청 수신
 * 2. Redis에서 INCR url_counter 명령 실행하여 고유한 카운터 값 획득
 * 3. 카운터 값을 Base62로 변환하여 단축 코드 생성
 * 4. 단축 코드 ↔ 원본 URL 매핑 정보를 데이터베이스에 저장
 * 5. 단축 URL 응답 반환
 * 
 * 동작 프로세스 (URL 조회):
 * 1. 단축 코드로 데이터베이스에서 원본 URL 조회
 * 2. URL이 존재하면 반환, 없으면 예외 발생
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    // URL 데이터 저장소 (JPA Repository)
    private final UrlRepository urlRepository;

    // Base62 인코딩/디코딩 서비스
    private final Base62 base62;

    // Redis 전역 카운터 서비스
    private final RedisCounterService redisCounterService;

    // URL 캐시 서비스
    private final UrlCacheService urlCacheService;

    // 애플리케이션 도메인 (application.properties에서 주입)
    @Value("${app.domain:http://localhost:8080}")
    private String domain;

    // 날짜 형식 파서
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 원본 URL을 단축 URL로 변환합니다.
     * 
     * 이 메서드는 URL 단축 서비스의 핵심 기능으로, 다음과 같은 과정을 수행합니다:
     * 
     * 1. 고유 ID 생성: Redis의 원자적 INCR 명령으로 중복되지 않는 카운터 값 획득
     * 2. 코드 변환: 카운터 값을 Base62로 인코딩하여 짧고 읽기 쉬운 문자열 생성
     * 3. 데이터 저장: 단축 코드와 원본 URL의 매핑 정보를 데이터베이스에 저장
     * 
     * 트랜잭션 보장:
     * - @Transactional 어노테이션으로 데이터 일관성 보장
     * - Redis 카운터 증가와 DB 저장이 원자적으로 처리되지는 않지만,
     * Redis INCR의 원자성으로 카운터 중복을 방지
     * 
     * 성능 특징:
     * - Redis INCR: O(1) 시간 복잡도로 매우 빠른 고유 ID 생성
     * - Base62 인코딩: 메모리 효율적인 문자열 변환
     * - 단순한 매핑 테이블: 빠른 저장과 조회
     * 
     * @param originalUrl 단축할 원본 URL (예: "https://www.example.com/very/long/path")
     * @return 생성된 단축 코드 (예: "1A2B3C")
     */
    @Transactional
    public String createShortUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid original url");
        }
        originalUrl = originalUrl.trim();
        log.debug("Creating short URL");

        // 1단계: Redis에서 원자적으로 카운터 값을 증가시켜 고유한 ID 획득
        // INCR 명령은 원자적이므로 동시 요청이 와도 중복되지 않는 값을 보장
        long counter = redisCounterService.getNextCounter();
        log.debug("Retrieved counter value: {}", counter);

        // 2단계: 카운터 값을 Base62로 인코딩하여 단축 코드 생성
        // 숫자를 짧고 URL-safe한 문자열로 변환 (예: 12345678 -> "1A2B3C")
        String shortCode = base62.encode(counter);
        log.debug("Generated short code");

        // 3단계: URL 엔티티 생성 및 데이터베이스에 저장
        // 단축 코드와 원본 URL의 매핑 정보를 영구 저장
        Url url = new Url(originalUrl);
        url.setShortUrl(shortCode);
        urlRepository.save(url);

        log.info("Short URL created");
        return shortCode;
    }

    /**
     * 사용자 지정 alias와 만료일을 지원하는 URL 단축 메서드
     * 
     * @param request URL 단축 요청 (원본 URL, 선택적 alias, 선택적 만료일)
     * @return 생성된 단축 URL 정보
     * @throws IllegalArgumentException 잘못된 요청 데이터인 경우
     */
    @Transactional
    public CreateUrlResponse createShortUrl(CreateUrlRequest request) {
        // 입력값 검증
        validateCreateUrlRequest(request);

        String originalUrl = request.getOriginalUrl().trim();
        String alias = request.getAlias();
        String expirationDateStr = request.getExpirationDate();

        log.debug("Creating short URL with alias: {}", alias);

        // 만료일 파싱
        LocalDateTime expirationDate = parseExpirationDate(expirationDateStr);

        String shortCode;

        if (alias != null && !alias.isBlank()) {
            // 사용자 지정 alias 사용
            alias = alias.trim();
            validateAlias(alias);

            // alias 중복 검사
            if (urlRepository.existsByShortUrl(alias)) {
                throw new IllegalArgumentException("Alias already exists: " + alias);
            }

            shortCode = alias;
        } else {
            // 자동 생성된 단축코드 사용
            long counter = redisCounterService.getNextCounter();
            shortCode = base62.encode(counter);
        }

        // URL 엔티티 생성 및 저장
        Url url = new Url(originalUrl, expirationDate);
        url.setShortUrl(shortCode);
        urlRepository.save(url);

        String shortUrl = domain + "/" + shortCode;

        log.info("Short URL created: {} -> {}", shortCode, originalUrl);

        return new CreateUrlResponse(
                shortCode,
                shortUrl,
                originalUrl,
                expirationDateStr);
    }

    /**
     * 단축 코드로 원본 URL을 조회합니다. (캐시 우선)
     * 
     * 이 메서드는 URL 리다이렉션 기능의 핵심으로, 단축 코드를 받아서
     * 해당하는 원본 URL을 찾아 반환합니다.
     * 
     * 동작 과정:
     * 1. Redis 캐시에서 조회 시도 (빠른 응답)
     * 2. 캐시 미스 시 데이터베이스에서 조회
     * 3. 데이터베이스 결과를 캐시에 저장
     * 4. URL이 존재하지 않으면 예외 발생
     * 
     * 성능 최적화:
     * - Redis 캐시 우선 조회로 응답 시간 단축 (목표: 100ms 미만)
     * - Cache-Aside 패턴으로 데이터베이스 부하 감소
     * - 캐시 히트율 모니터링 및 통계 수집
     * 
     * 예외 처리:
     * - 존재하지 않는 단축 코드에 대해 IllegalArgumentException 발생
     * - 컨트롤러에서 404 Not Found 응답으로 변환
     * 
     * @param shortCode 조회할 단축 코드 (예: "1A2B3C")
     * @return 해당하는 원본 URL (예: "https://www.example.com/very/long/path")
     * @throws IllegalArgumentException 단축 코드가 존재하지 않는 경우
     */
    public String getOriginalUrl(String shortCode) {
        log.debug("Retrieving original URL for short code: {}", shortCode);

        // 캐시 서비스를 통한 조회 (캐시 우선)
        return urlCacheService.getOriginalUrl(shortCode)
                .orElseThrow(() -> {
                    // 단축 코드가 존재하지 않는 경우 예외 발생
                    log.warn("No original URL found for short code: {}", shortCode);
                    return new IllegalArgumentException("Invalid short url");
                });

        // // Phase 1: 캐시 우회 - Repository 직접 호출
        // return urlRepository.findByShortUrl(shortCode)
        // .filter(url -> !url.isExpired())
        // .map(Url::getOriginalUrl)
        // .orElseThrow(() -> {
        // log.warn("No original URL found for short code: {}", shortCode);
        // return new IllegalArgumentException("Invalid short url");
        // });
    }

    /**
     * CreateUrlRequest 유효성 검사
     */
    private void validateCreateUrlRequest(CreateUrlRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (request.getOriginalUrl() == null || request.getOriginalUrl().isBlank()) {
            throw new IllegalArgumentException("Original URL cannot be null or empty");
        }
    }

    /**
     * Alias 유효성 검사
     */
    private void validateAlias(String alias) {
        if (alias.length() < 3) {
            throw new IllegalArgumentException("Alias must be at least 3 characters long");
        }

        if (alias.length() > 20) {
            throw new IllegalArgumentException("Alias must be at most 20 characters long");
        }

        // Base62 문자만 허용
        for (char c : alias.toCharArray()) {
            if (!isValidBase62Char(c)) {
                throw new IllegalArgumentException("Alias can only contain alphanumeric characters (0-9, a-z, A-Z)");
            }
        }
    }

    /**
     * Base62 유효한 문자인지 확인
     */
    private boolean isValidBase62Char(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z');
    }

    /**
     * 만료일 문자열을 LocalDateTime으로 파싱
     */
    private LocalDateTime parseExpirationDate(String expirationDateStr) {
        if (expirationDateStr == null || expirationDateStr.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(expirationDateStr.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid expiration date format. Use ISO format: yyyy-MM-ddTHH:mm:ss",
                    e);
        }
    }
}
