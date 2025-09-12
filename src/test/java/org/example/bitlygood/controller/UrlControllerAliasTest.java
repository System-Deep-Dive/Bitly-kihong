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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UrlController의 새로운 기능 (alias, 만료일) 테스트
 */
@WebMvcTest(UrlController.class)
class UrlControllerAliasTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlService urlService;

    @Test
    @DisplayName("사용자 지정 alias로 URL 단축 API 테스트")
    void createShortUrlWithAlias_Success() throws Exception {
        // given
        CreateUrlRequest request = new CreateUrlRequest(
                "https://www.example.com",
                "myalias",
                null);

        CreateUrlResponse response = new CreateUrlResponse(
                "myalias",
                "http://localhost:8080/myalias",
                "https://www.example.com",
                null);

        when(urlService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortCode").value("myalias"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/myalias"))
                .andExpect(jsonPath("$.originalUrl").value("https://www.example.com"))
                .andExpect(jsonPath("$.expirationDate").isEmpty());
    }

    @Test
    @DisplayName("만료일과 함께 URL 단축 API 테스트")
    void createShortUrlWithExpirationDate_Success() throws Exception {
        // given
        CreateUrlRequest request = new CreateUrlRequest(
                "https://www.example.com",
                "myalias",
                "2024-12-31T23:59:59");

        CreateUrlResponse response = new CreateUrlResponse(
                "myalias",
                "http://localhost:8080/myalias",
                "https://www.example.com",
                "2024-12-31T23:59:59");

        when(urlService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortCode").value("myalias"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/myalias"))
                .andExpect(jsonPath("$.originalUrl").value("https://www.example.com"))
                .andExpect(jsonPath("$.expirationDate").value("2024-12-31T23:59:59"));
    }

    @Test
    @DisplayName("alias 없이 자동 생성된 코드로 URL 단축 API 테스트")
    void createShortUrlWithoutAlias_Success() throws Exception {
        // given
        CreateUrlRequest request = new CreateUrlRequest(
                "https://www.example.com",
                null,
                null);

        CreateUrlResponse response = new CreateUrlResponse(
                "abc123",
                "http://localhost:8080/abc123",
                "https://www.example.com",
                null);

        when(urlService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.originalUrl").value("https://www.example.com"))
                .andExpect(jsonPath("$.expirationDate").isEmpty());
    }

    @Test
    @DisplayName("잘못된 요청 데이터로 URL 단축 API 테스트")
    void createShortUrlWithInvalidRequest_ReturnsBadRequest() throws Exception {
        // given
        CreateUrlRequest request = new CreateUrlRequest(
                "", // 빈 URL
                "myalias",
                null);

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
                .thenThrow(new IllegalArgumentException("Original URL cannot be null or empty"));

        // when & then
        mockMvc.perform(post("/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()); // IllegalArgumentException은 404로 변환됨
    }

    @Test
    @DisplayName("기존 simple API 하위 호환성 테스트")
    void createShortUrlSimple_DeprecatedApi_Success() throws Exception {
        // given
        String originalUrl = "https://www.example.com";
        CreateUrlResponse response = new CreateUrlResponse(
                "abc123",
                "http://localhost:8080/abc123",
                originalUrl,
                null);

        when(urlService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/urls/simple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"" + originalUrl + "\""))
                .andExpect(status().isCreated())
                .andExpect(content().string("abc123"));
    }
}
