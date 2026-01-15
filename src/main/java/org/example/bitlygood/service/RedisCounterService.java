package org.example.bitlygood.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis를 사용한 전역 카운터 서비스
 * 
 * 이 서비스는 URL 단축 서비스의 핵심 컴포넌트로, Redis의 INCR 명령의 원자성을 활용하여
 * 다중 서버 환경에서도 안전하게 고유한 카운터 값을 제공합니다.
 * 
 */
@Service
@RequiredArgsConstructor
@Observed(name = "redis.counter", contextualName = "redis-counter-service")
public class RedisCounterService {

    // Redis와 상호작용하기 위한 템플릿 객체
    private final RedisTemplate<String, String> redisTemplate;

    // Redis에 저장될 카운터 키 이름 (전역적으로 고유해야 함)
    private static final String COUNTER_KEY = "url_counter";

    /**
     * Redis의 INCR 명령을 사용하여 원자적으로 카운터 값을 증가시키고 반환합니다.
     * 
     * 이 메서드는 URL 단축 서비스의 핵심 기능으로, 다음과 같은 과정을 수행합니다:
     * 1. Redis에서 "url_counter" 키의 현재 값을 조회
     * 2. 해당 값을 1 증가시킴 (원자적 연산)
     * 3. 증가된 값을 반환
     * 
     * Redis INCR 명령의 특징:
     * - 원자적(Atomic) 연산: 다른 클라이언트의 간섭 없이 안전하게 실행
     * - 키가 존재하지 않으면 0에서 시작하여 1을 반환
     * - 여러 서버에서 동시 호출해도 중복되지 않는 고유한 값 보장
     * 
     * NPE (NullPointerException)가 발생할 수 있는 상황:
     * - 내부적으로 Redis에 예기치 않은 문제가 생기거나, 직렬화 오류 등으로 인해 값이 정상적으로 반환되지 않는 경우 NULL 발생.
     * - 이후 반환값(Long next)이 null인 상태에서 long 기본형으로 반환하려 하거나 후속 연산에
     * 사용하면 NPE(NullPointerException) 가 발생합니다.
     * 
     * @return 증가된 카운터 값 (최초 호출 시 1, 이후 순차적으로 증가)
     */
    @Observed(name = "redis.counter.getNextCounter", contextualName = "redis-incr-counter")
    public long getNextCounter() {
        Long next = redisTemplate.opsForValue().increment(COUNTER_KEY);
        if (next == null) {
            throw new IllegalStateException("Redis INCR returned null for key: " + COUNTER_KEY);
        }
        return next;
    }

    /**
     * 현재 카운터 값을 조회합니다.
     * 
     * 이 메서드는 Redis에 저장된 현재 카운터 값을 조회하여 반환합니다.
     * 주로 시스템 상태 모니터링, 디버깅, 또는 통계 목적으로 사용됩니다.
     * 
     * 반환 값 규칙:
     * - 카운터가 존재하는 경우: 해당 숫자 값을 long 타입으로 변환하여 반환
     * - 카운터가 존재하지 않는 경우: 0을 반환 (시스템 초기화 상태)
     * 
     * @return 현재 카운터 값 (카운터가 설정되지 않은 경우 0)
     */
    public long getCurrentCounter() {
        String value = redisTemplate.opsForValue().get(COUNTER_KEY);
        if (value == null)
            return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Counter value is not numeric: " + value, ex);
        }
    }

    /**
     * 카운터를 초기화합니다.
     * 
     * 주의: 이 메서드는 주로 테스트 환경에서 사용되며, 프로덕션 환경에서는
     * 신중하게 사용해야 합니다. 카운터를 초기화하면 기존에 생성된 단축 URL과
     * 새로운 단축 URL 간에 충돌이 발생할 수 있습니다.
     * 
     * 사용 시나리오:
     * - 단위 테스트나 통합 테스트에서 테스트 데이터 초기화
     * - 개발 환경에서 시스템 재설정
     * - 전체 시스템 마이그레이션 시
     * 
     * @param initialValue 카운터를 설정할 초기값 (일반적으로 0 또는 1)
     */
    public void resetCounter(long initialValue) {
        // Redis SET 명령으로 카운터를 지정된 값으로 초기화
        redisTemplate.opsForValue().set(COUNTER_KEY, String.valueOf(initialValue));
    }
}
