package org.example.bitlygood.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    private final Base62 base62 = new Base62();

    @Test
    @DisplayName("숫자를 Base62로 인코딩한다.")
    void encode() {
        // 기본적인 인코딩 테스트
        assertEquals("0", base62.encode(0L));
        assertEquals("1", base62.encode(1L));
        assertEquals("2", base62.encode(2L));
        assertEquals("9", base62.encode(9L));
        assertEquals("a", base62.encode(10L));
        assertEquals("z", base62.encode(35L));
        assertEquals("A", base62.encode(36L));
        assertEquals("Z", base62.encode(61L));

        // 62진법 경계값 테스트
        assertEquals("10", base62.encode(62L));
        assertEquals("11", base62.encode(63L));
        assertEquals("1z", base62.encode(97L));
        assertEquals("1Z", base62.encode(123L));

        // // 큰 수 테스트
        assertEquals("PNFQ", base62.encode(12345678L));
    }

    @Test
    @DisplayName("Base62 문자열을 숫자로 디코딩한다.")
    void decode() {
        // 기본적인 디코딩 테스트
        assertEquals(0L, base62.decode("0"));
        assertEquals(1L, base62.decode("1"));
        assertEquals(2L, base62.decode("2"));
        assertEquals(9L, base62.decode("9"));
        assertEquals(10L, base62.decode("a"));
        assertEquals(35L, base62.decode("z"));
        assertEquals(36L, base62.decode("A"));
        assertEquals(61L, base62.decode("Z"));

        // 62진법 경계값 테스트
        assertEquals(62L, base62.decode("10"));
        assertEquals(63L, base62.decode("11"));
        assertEquals(97L, base62.decode("1z"));
        assertEquals(123L, base62.decode("1Z"));

        // // 큰 수 테스트
        assertEquals(12345678L, base62.decode("PNFQ"));
    }

    @Test
    @DisplayName("인코딩과 디코딩 결과는 원래 값과 동일해야 한다.")
    void encodeAndDecode() {
        long[] testValues = { 0L, 1L, 10L, 62L, 100L, 1000L, 10000L, 12345678L, Long.MAX_VALUE };

        for (long originalValue : testValues) {
            String encoded = base62.encode(originalValue);
            long decoded = base62.decode(encoded);
            assertEquals(originalValue, decoded,
                    "Failed for value: " + originalValue + ", encoded: " + encoded);
        }
    }

    @Test
    @DisplayName("잘못된 Base62 문자열 디코딩 시 예외가 발생한다.")
    void decode_InvalidString() {
        // null 또는 빈 문자열
        assertThrows(IllegalArgumentException.class, () -> base62.decode(null));
        assertThrows(IllegalArgumentException.class, () -> base62.decode(""));

        // 잘못된 문자 포함
        assertThrows(IllegalArgumentException.class, () -> base62.decode("abc!"));
        assertThrows(IllegalArgumentException.class, () -> base62.decode("abc@"));
        assertThrows(IllegalArgumentException.class, () -> base62.decode("abc#"));
    }

    @Test
    @DisplayName("Base62 문자 집합의 모든 문자를 올바르게 처리한다.")
    void allBase62Characters() {
        String allChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        for (int i = 0; i < allChars.length(); i++) {
            char c = allChars.charAt(i);
            String singleChar = String.valueOf(c);

            // 단일 문자 인코딩/디코딩 테스트
            long decoded = base62.decode(singleChar);
            String encoded = base62.encode(decoded);
            assertEquals(singleChar, encoded, "Failed for character: " + c);
        }
    }
}
