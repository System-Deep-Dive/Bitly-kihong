package org.example.bitlygood.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.example.bitlygood.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlService urlService;

    @Test
    @DisplayName("POST /urls 요청 시 단축 URL을 생성하고 201 응답을 반환한다.")
    void createShortUrl() throws Exception {
        // given
        String originalUrl = "https://example.com";
        String shortUrl = "B";

        CreateUrlResponse response = new CreateUrlResponse(
                shortUrl,
                "http://localhost:8080/" + shortUrl,
                originalUrl,
                null);

        when(urlService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateUrlRequest(originalUrl, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(shortUrl))
                .andExpect(jsonPath("$.originalUrl").value(originalUrl));
    }

    @Test
    @DisplayName("GET /{shortUrl} 요청 시 원본 URL로 리디렉션한다.")
    void redirect() throws Exception {
        // given
        String shortUrl = "B";
        String originalUrl = "https://example.com";
        when(urlService.getOriginalUrl(shortUrl)).thenReturn(originalUrl);

        // when & then
        mockMvc.perform(get("/" + shortUrl))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", originalUrl));
    }

    @Test
    @DisplayName("GET /{shortUrl} 요청 시 URL을 찾지 못하면 404 응답을 반환한다.")
    void redirect_notFound() throws Exception {
        // given
        String shortUrl = "nonexistent";
        when(urlService.getOriginalUrl(shortUrl)).thenThrow(new IllegalArgumentException());

        // when & then
        mockMvc.perform(get("/" + shortUrl))
                .andExpect(status().isNotFound());
    }
}
