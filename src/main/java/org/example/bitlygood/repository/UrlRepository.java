package org.example.bitlygood.repository;

import org.example.bitlygood.domain.Url;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findByShortUrl(String shortUrl);

    /**
     * 주어진 단축코드(alias)가 이미 존재하는지 확인합니다.
     * 
     * @param shortUrl 확인할 단축코드
     * @return 존재하면 true, 존재하지 않으면 false
     */
    boolean existsByShortUrl(String shortUrl);
}
