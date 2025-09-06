package org.example.bitlygood.service;

import lombok.RequiredArgsConstructor;
import org.example.bitlygood.domain.Url;
import org.example.bitlygood.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final Base62 base62;

    @Transactional
    public String createShortUrl(String originalUrl) {
        Url url = new Url(originalUrl);
        Url savedUrl = urlRepository.save(url);

        String shortUrl = base62.encode(savedUrl.getId());
        savedUrl.setShortUrl(shortUrl);

        return shortUrl;
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortUrl) {
        return urlRepository.findByShortUrl(shortUrl)
                .map(Url::getOriginalUrl)
                .orElseThrow(() -> new IllegalArgumentException("Invalid short url"));
    }
}
