package org.example.bitlygood.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Redis 카운터 서비스 단위 테스트
 * 
 * 이 테스트 클래스는 RedisCounterService의 핵심 기능을 검증합니다:
 * - Redis INCR 명령을 통한 원자적 카운터 증가
 * - 현재 카운터 값 조회
 * - 카운터 초기화 기능
 * 
 * 실제 Redis 서버 없이 Mock 객체를 사용하여 빠르고 안정적인 테스트를 수행합니다.
 */
@ExtendWith(MockitoExtension.class)
class RedisCounterServiceTest {

    // RedisTemplate을 Mock으로 생성 (실제 Redis 연결 없이 테스트)
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    // ValueOperations를 Mock으로 생성 (Redis의 문자열 값 연산을 모킹)
    @Mock
    private ValueOperations<String, String> valueOperations;

    // 테스트 대상 서비스에 Mock 객체들을 주입
    @InjectMocks
    private RedisCounterService redisCounterService;

    /**
     * Redis INCR 명령을 사용한 카운터 증가 테스트
     * 
     * Redis의 INCR 명령은 원자적(atomic) 연산으로, 동시에 여러 요청이 들어와도
     * 중복되지 않는 고유한 카운터 값을 보장합니다.
     */
    @Test
    @DisplayName("Redis INCR 명령을 사용하여 카운터 값을 증가시키고 반환한다.")
    void getNextCounter() {
        // given - Mock 설정: RedisTemplate과 ValueOperations 동작 정의
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // when - 실제 메서드 호출
        long result = redisCounterService.getNextCounter();

        // then - 결과 검증: 반환값과 메서드 호출 횟수 확인
        assertEquals(1L, result);
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).increment("url_counter");
    }

    /**
     * 순차적 카운터 증가 테스트
     * 
     * 여러 번 호출할 때마다 카운터가 순차적으로 증가하는지 확인합니다.
     * 이는 URL 단축 서비스에서 고유한 단축 코드 생성의 핵심 요구사항입니다.
     */
    @Test
    @DisplayName("여러 번 호출 시 순차적으로 증가된 카운터 값을 반환한다.")
    void getNextCounter_MultipleCalls() {
        // given - 연속된 호출에 대해 각각 다른 값을 반환하도록 Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString()))
                .thenReturn(1L) // 첫 번째 호출: 1 반환
                .thenReturn(2L) // 두 번째 호출: 2 반환
                .thenReturn(3L); // 세 번째 호출: 3 반환

        // when - 연속으로 3번 호출
        long result1 = redisCounterService.getNextCounter();
        long result2 = redisCounterService.getNextCounter();
        long result3 = redisCounterService.getNextCounter();

        // then - 각 호출마다 순차적으로 증가하는 값이 반환되는지 확인
        assertEquals(1L, result1);
        assertEquals(2L, result2);
        assertEquals(3L, result3);

        // INCR 명령이 정확히 3번 호출되었는지 검증
        verify(valueOperations, times(3)).increment("url_counter");
    }

    /**
     * 현재 카운터 값 조회 테스트
     * 
     * Redis에 저장된 현재 카운터 값을 조회하는 기능을 테스트합니다.
     * 이는 시스템 상태 모니터링이나 디버깅에 유용합니다.
     */
    @Test
    @DisplayName("현재 카운터 값을 조회한다.")
    void getCurrentCounter() {
        // given - Redis에서 "42"라는 문자열 값을 반환하도록 Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url_counter")).thenReturn("42");

        // when - 현재 카운터 값 조회
        long result = redisCounterService.getCurrentCounter();

        // then - 문자열 "42"가 long 타입 42L로 변환되어 반환되는지 확인
        assertEquals(42L, result);
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get("url_counter");
    }

    /**
     * 카운터가 설정되지 않은 경우의 처리 테스트
     * 
     * Redis에 카운터 키가 존재하지 않을 때(null 반환) 0을 반환하는지 확인합니다.
     * 이는 새로운 시스템 초기화 시나 Redis 재시작 후의 상황을 시뮬레이션합니다.
     */
    @Test
    @DisplayName("카운터가 설정되지 않은 경우 0을 반환한다.")
    void getCurrentCounter_NotSet() {
        // given - Redis에서 null을 반환하도록 Mock 설정 (키가 존재하지 않음)
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url_counter")).thenReturn(null);

        // when - 현재 카운터 값 조회
        long result = redisCounterService.getCurrentCounter();

        // then - null인 경우 0L을 반환하는지 확인
        assertEquals(0L, result);
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get("url_counter");
    }

    /**
     * 카운터 초기화 테스트
     * 
     * 카운터를 특정 값으로 초기화하는 기능을 테스트합니다.
     * 주로 테스트 환경에서 사용되며, 프로덕션에서는 신중하게 사용해야 합니다.
     */
    @Test
    @DisplayName("카운터를 초기화한다.")
    void resetCounter() {
        // given - Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when - 카운터를 100으로 초기화
        redisCounterService.resetCounter(100L);

        // then - Redis SET 명령이 올바른 값으로 호출되었는지 확인
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).set("url_counter", "100");
    }

    /**
     * 카운터를 0으로 초기화하는 테스트
     * 
     * 카운터를 0으로 리셋하는 특별한 케이스를 테스트합니다.
     * 이는 새로운 시스템 시작이나 전체 재설정 시나리오를 시뮬레이션합니다.
     */
    @Test
    @DisplayName("카운터를 0으로 초기화한다.")
    void resetCounter_ToZero() {
        // given - Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when - 카운터를 0으로 초기화
        redisCounterService.resetCounter(0L);

        // then - Redis SET 명령이 "0"으로 호출되었는지 확인
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).set("url_counter", "0");
    }
}
