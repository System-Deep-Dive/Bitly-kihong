package org.example.bitlygood.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    private final Base62 base62 = new Base62();

    @Test
    @DisplayName("ID를 Base62로 인코딩한다.")
    void encode() {
        assertEquals("B", base62.encode(1L));
        assertEquals("C", base62.encode(2L));
        assertEquals("BA", base62.encode(62L));
        assertEquals("BB", base62.encode(63L));
    }

    @Test
    @DisplayName("Base62 문자열을 ID로 디코딩한다.")
    void decode() {
        assertEquals(1L, base62.decode("B"));
        assertEquals(2L, base62.decode("C"));
        assertEquals(62L, base62.decode("BA"));
        assertEquals(63L, base62.decode("BB"));
    }

    @Test
    @DisplayName("인코딩과 디코딩 결과는 원래 값과 동일해야 한다.")
    void encodeAndDecode() {
        long originalId = 12345L;
        String encoded = base62.encode(originalId);
        long decoded = base62.decode(encoded);
        assertEquals(originalId, decoded);
    }
}
