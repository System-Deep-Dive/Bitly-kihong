/**
 * k6 InfluxDB 출력 설정 최적화
 * 
 * 이 파일은 k6 테스트 실행 시 InfluxDB로의 데이터 전송을 최적화합니다.
 * 배치 크기를 제한하여 "Request Entity Too Large" 에러를 방지합니다.
 */

export const options = {
  // InfluxDB 출력 설정
  ext: {
    loadimpact: {
      name: 'Bitly Performance Test',
      projectID: 1,
    },
  },
  
  // InfluxDB 배치 설정 최적화
  influxdb: {
    // 배치 크기 제한 (기본값보다 작게 설정)
    batchSize: 1000,
    // 배치 간격 설정 (밀리초)
    batchInterval: '1s',
    // 연결 타임아웃 설정
    timeout: '30s',
    // 재시도 설정
    retries: 3,
    // 압축 사용
    compression: true,
  },
};

// k6 실행 시 사용법:
// k6 run --out influxdb=http://localhost:8086/k6 k6-influxdb-config.js scenario-b-minimal.js

