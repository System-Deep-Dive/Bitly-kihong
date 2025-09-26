package org.example.bitlygood.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * URL 단축 응답 DTO
 * 
 * URL 단축 요청에 대한 응답 객체입니다.
 * 생성된 단축코드와 전체 단축 URL을 포함합니다.
 */
@Getter
@AllArgsConstructor
public class CreateUrlResponse {

    /**
     * 생성된 단축코드 (alias 또는 자동 생성된 코드)
     */
    private String shortCode;

    /**
     * 전체 단축 URL
     * 도메인과 단축코드를 조합한 완전한 URL
     */
    private String shortUrl;

    /**
     * 원본 URL
     */
    private String originalUrl;

    /**
     * 만료일 (있는 경우)
     */
    private String expirationDate;
}
