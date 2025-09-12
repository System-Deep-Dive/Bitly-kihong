package org.example.bitlygood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 * 
 * 이 설정 클래스는 Spring Boot 애플리케이션에서 Redis와 상호작용하기 위한
 * RedisTemplate Bean을 구성합니다.
 * 
 * 주요 구성 요소:
 * - RedisTemplate: Redis와의 모든 상호작용을 담당하는 핵심 컴포넌트
 * - 직렬화 설정: 키와 값의 직렬화/역직렬화 방식 정의
 * - 연결 팩토리: Redis 서버와의 연결 관리
 * 
 * 이 프로젝트에서는 String 타입의 키와 값을 사용하므로,
 * StringRedisSerializer를 사용하여 성능을 최적화합니다.
 */
@Configuration
public class RedisConfig {

    /**
     * 
     * RedisTemplate Bean을 생성하고 구성합니다.
     * 
     * RedisTemplate은 Spring Data Redis의 핵심 컴포넌트로, 다음과 같은 기능을 제공합니다:
     * - Redis 명령어 실행 (SET, GET, INCR, DEL 등)
     * - 자동 직렬화/역직렬화
     * - 연결 풀 관리
     * - 트랜잭션 지원
     * 
     * 직렬화 설정:
     * - 키(Key): StringRedisSerializer 사용 (성능 최적화)
     * - 값(Value): StringRedisSerializer 사용 (간단한 문자열 저장)
     * - 해시 키/값: StringRedisSerializer 사용 (일관성 유지)
     * 
     * StringRedisSerializer를 사용하는 이유:
     * - 문자열 데이터에 최적화된 성능
     * - Redis CLI에서 직접 확인 가능한 가독성
     * - 디버깅과 모니터링의 편의성
     * 
     * @param connectionFactory Redis 연결 팩토리 (application.properties에서 자동 주입)
     * @return 구성된 RedisTemplate (String 타입 키/값 전용)
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        // RedisTemplate 인스턴스 생성 (키와 값 모두 String 타입)
        RedisTemplate<String, String> template = new RedisTemplate<>();

        // Redis 연결 팩토리 설정 (application.properties의 Redis 설정 사용)
        template.setConnectionFactory(connectionFactory);

        // String 직렬화기 생성 (성능 최적화를 위한 단일 인스턴스 사용)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 모든 직렬화 설정을 StringRedisSerializer로 통일
        // 이렇게 하면 Redis에 저장된 데이터가 문자열로 저장되어 가독성이 좋아집니다
        template.setKeySerializer(stringSerializer); // 키 직렬화
        template.setValueSerializer(stringSerializer); // 값 직렬화
        template.setHashKeySerializer(stringSerializer); // 해시 키 직렬화
        template.setHashValueSerializer(stringSerializer); // 해시 값 직렬화

        // 모든 설정이 완료된 후 템플릿 초기화
        // 이 메서드를 호출하지 않으면 RedisTemplate이 제대로 동작하지 않습니다
        template.afterPropertiesSet();

        return template;
    }
}
