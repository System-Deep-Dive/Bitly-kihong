package org.example.bitlygood.service;

import org.springframework.stereotype.Component;

/**
 * Base62 인코딩/디코딩 서비스
 * 
 * 
 */
@Component
public class Base62 {

    private static final char[] BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            .toCharArray();

    private static final int BASE = 62;

    /**
     * @param value 인코딩할 숫자 (0 이상의 정수)
     * @return Base62로 인코딩된 문자열
     */
    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        // 0에 대한 특별 처리: Base62에서 0은 "0"으로 표현
        if (value == 0) {
            return "0";
        }

        // 결과를 저장할 StringBuilder (문자열 조합을 효율적으로 처리)
        StringBuilder sb = new StringBuilder();

        // 62진법 변환: 0이 될 때까지 62로 나누기 반복
        while (value > 0) {
            // 나머지를 Base62 문자로 변환하여 추가
            sb.append(BASE62_CHARS[(int) (value % BASE)]);
            // 몫을 새로운 값으로 설정 (정수 나눗셈)
            value /= BASE;
        }

        // 역순으로 배치: 나머지부터 계산했으므로 순서를 뒤집어야 함
        return sb.reverse().toString();
    }

    /**
     * @param encoded Base62로 인코딩된 문자열 (null이거나 빈 문자열이면 안됨)
     * @return 디코딩된 숫자
     * @throws IllegalArgumentException 잘못된 Base62 문자열인 경우
     */
    public long decode(String encoded) {
        // 입력값 유효성 검사
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string cannot be null or empty");
        }

        // 결과를 누적할 변수와 62의 거듭제곱을 저장할 변수
        long result = 0;
        long power = 1; // 62^0 = 1부터 시작

        // 오른쪽부터 왼쪽으로 문자를 하나씩 처리 (가장 낮은 자릿수부터)
        for (int i = encoded.length() - 1; i >= 0; i--) {
            char c = encoded.charAt(i);

            // 문자를 해당하는 숫자(0-61)로 변환
            int digit = getCharIndex(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid character in Base62 string: " + c);
            }

            // 해당 자릿수의 값을 누적: 숫자 * 62^자릿수
            result += digit * power;

            // 다음 자릿수를 위해 62의 거듭제곱 증가
            power *= BASE;
        }

        return result;
    }

    /**
     * 문자에 해당하는 인덱스를 반환합니다.
     * 
     * @param c Base62 문자 (0-9, a-z, A-Z 중 하나)
     * @return 문자에 해당하는 인덱스 (0-61), 잘못된 문자인 경우 -1
     */
    private int getCharIndex(char c) {
        // BASE62_CHARS 배열을 순회하며 해당 문자를 찾음
        for (int i = 0; i < BASE62_CHARS.length; i++) {
            if (BASE62_CHARS[i] == c) {
                return i; // 찾은 경우 해당 인덱스 반환
            }
        }
        // 찾지 못한 경우 -1 반환 (잘못된 문자)
        return -1;
    }
}
