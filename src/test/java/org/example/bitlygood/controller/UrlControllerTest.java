package org.example.bitlygood.controller;

import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.example.bitlygood.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

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

}
