package org.example.bitlygood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "url", indexes = {
        @Index(name = "idx_url_short_url", columnList = "shortUrl"),
        @Index(name = "idx_url_expiration_date", columnList = "expirationDate"),
        @Index(name = "idx_url_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalUrl;

    @Column(nullable = false, unique = true)
    private String shortUrl;

    @Column
    private LocalDateTime expirationDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Url(String originalUrl) {
        this.originalUrl = originalUrl;
        this.createdAt = LocalDateTime.now();
    }

    public Url(String originalUrl, LocalDateTime expirationDate) {
        this.originalUrl = originalUrl;
        this.expirationDate = expirationDate;
        this.createdAt = LocalDateTime.now();
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public void setExpirationDate(LocalDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * URL이 만료되었는지 확인합니다.
     * 
     * @return 만료일이 설정되어 있고 현재 시간이 만료일을 지났으면 true
     */
    public boolean isExpired() {
        return expirationDate != null && LocalDateTime.now().isAfter(expirationDate);
    }
}