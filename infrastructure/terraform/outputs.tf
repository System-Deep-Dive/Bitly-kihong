# Terraform 출력 정의
# GCP 배포 후 필요한 정보들을 출력합니다.

output "environment_variables" {
  description = "Docker Compose에서 사용할 환경 변수들"
  value = {
    # PostgreSQL
    POSTGRES_DB                      = var.postgres_db
    POSTGRES_USER                    = var.postgres_user
    POSTGRES_MAX_CONNECTIONS         = var.postgres_max_connections
    POSTGRES_SHARED_BUFFERS          = var.postgres_shared_buffers
    POSTGRES_EFFECTIVE_CACHE_SIZE    = var.postgres_effective_cache_size
    
    # Spring Boot
    SPRING_PROFILES_ACTIVE      = var.spring_profiles_active
    SPRING_DATASOURCE_USERNAME  = var.postgres_user
    SPRING_DATASOURCE_URL       = "jdbc:postgresql://pgbouncer:6432/${var.postgres_db}"
    SPRING_DATA_REDIS_HOST      = "redis"
    SPRING_DATA_REDIS_PORT      = "6379"
    
    # Application
    APP_DOMAIN = var.app_domain
    
    # Grafana
    GRAFANA_ADMIN_PASSWORD = var.grafana_admin_password
    
    # InfluxDB
    INFLUXDB_DB              = var.influxdb_db
    INFLUXDB_ADMIN_USER      = var.influxdb_admin_user
    INFLUXDB_USER            = var.influxdb_user
    
    # Exporters
    REDIS_EXPORTER_ADDR             = "redis://redis:6379"
    POSTGRES_EXPORTER_DATA_SOURCE_NAME = "postgresql://${var.postgres_user}:${var.postgres_password}@postgres:5432/${var.postgres_db}?sslmode=disable"
  }
  sensitive = false
}

output "secret_manager_secrets" {
  description = "GCP Secret Manager에 저장해야 할 비밀 정보 목록"
  value = [
    "postgres_password",
    "grafana_admin_password",
    "influxdb_admin_password",
    "influxdb_user_password",
  ]
}
