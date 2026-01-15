package org.example.bitlygood.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.bitlygood.domain.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findByShortUrl(String shortUrl);

    /**
     * 주어진 단축코드(alias)가 이미 존재하는지 확인합니다.
     * 
     * @param shortUrl 확인할 단축코드
     * @return 존재하면 true, 존재하지 않으면 false
     */
    boolean existsByShortUrl(String shortUrl);

    /**
     * 만료된 URL들을 조회합니다.
     * 
     * @param currentTime 현재 시간
     * @return 만료된 URL 목록
     */
    @Query("SELECT u FROM Url u WHERE u.expirationDate IS NOT NULL AND u.expirationDate < :currentTime")
    List<Url> findExpiredUrls(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 만료된 URL들을 삭제합니다.
     * 
     * @param currentTime 현재 시간
     * @return 삭제된 URL 개수
     */
    @Modifying
    @Query("DELETE FROM Url u WHERE u.expirationDate IS NOT NULL AND u.expirationDate < :currentTime")
    int deleteExpiredUrls(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 특정 기간 동안 생성된 URL 개수를 조회합니다.
     * 
     * @param startTime 시작 시간
     * @param endTime   종료 시간
     * @return URL 개수
     */
    @Query("SELECT COUNT(u) FROM Url u WHERE u.createdAt BETWEEN :startTime AND :endTime")
    long countByCreatedAtBetween(@Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 단축코드로 원본 URL만 조회합니다. (최적화된 쿼리)
     * 만료 체크도 DB 레벨에서 수행합니다.
     * 
     * @param shortUrl 단축코드
     * @return 원본 URL (Optional)
     */
    @Query(value = "SELECT original_url FROM url WHERE short_url = :shortUrl AND (expiration_date IS NULL OR expiration_date > NOW())", nativeQuery = true)
    Optional<String> findOriginalUrlByShortUrlNotExpired(@Param("shortUrl") String shortUrl);
}
