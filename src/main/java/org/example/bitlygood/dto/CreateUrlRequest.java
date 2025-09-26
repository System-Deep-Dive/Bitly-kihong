package org.example.bitlygood.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * URL 단축 요청 DTO
 * 
 * 사용자가 URL 단축을 요청할 때 사용하는 요청 객체입니다.
 * 원본 URL과 선택적으로 사용자 지정 alias를 포함할 수 있습니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    /**
     * 단축할 원본 URL
     * 필수 필드이며, null이거나 빈 문자열이면 안됩니다.
     */
    private String originalUrl;

    /**
     * 사용자 지정 alias (별칭)
     * 선택적 필드이며, null이면 자동으로 단축코드가 생성됩니다.
     * 지정된 경우 중복 검사를 수행합니다.
     */
    private String alias;

    /**
     * 만료일 (선택적)
     * ISO 8601 형식의 날짜 문자열 (예: "2024-12-31T23:59:59")
     * null이면 만료일이 없습니다.
     */
    private String expirationDate;
}
