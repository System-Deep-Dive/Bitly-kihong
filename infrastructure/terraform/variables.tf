# Terraform 변수 정의
# GCP 배포 시 사용할 환경 변수들을 정의합니다.

variable "project_id" {
  description = "GCP 프로젝트 ID"
  type        = string
}

variable "region" {
  description = "GCP 리전"
  type        = string
  default     = "asia-northeast3"
}

variable "zone" {
  description = "GCP 존"
  type        = string
  default     = "asia-northeast3-a"
}

# PostgreSQL 설정
variable "postgres_db" {
  description = "PostgreSQL 데이터베이스 이름"
  type        = string
}

variable "postgres_user" {
  description = "PostgreSQL 사용자 이름"
  type        = string
}

variable "postgres_password" {
  description = "PostgreSQL 비밀번호 (Secret Manager에 저장)"
  type        = string
  sensitive   = true
}

variable "postgres_max_connections" {
  description = "PostgreSQL 최대 연결 수"
  type        = string
  default     = "500"
}

variable "postgres_shared_buffers" {
  description = "PostgreSQL 공유 버퍼 크기"
  type        = string
  default     = "512MB"
}

variable "postgres_effective_cache_size" {
  description = "PostgreSQL 유효 캐시 크기"
  type        = string
  default     = "1536MB"
}

# Spring Boot 설정
variable "spring_profiles_active" {
  description = "Spring Boot 프로파일"
  type        = string
  default     = "docker"
}

variable "app_domain" {
  description = "애플리케이션 도메인"
  type        = string
}

# Grafana 설정
variable "grafana_admin_password" {
  description = "Grafana 관리자 비밀번호 (Secret Manager에 저장)"
  type        = string
  sensitive   = true
}

# InfluxDB 설정
variable "influxdb_db" {
  description = "InfluxDB 데이터베이스 이름"
  type        = string
  default     = "k6"
}

variable "influxdb_admin_user" {
  description = "InfluxDB 관리자 사용자 이름"
  type        = string
  default     = "admin"
}

variable "influxdb_admin_password" {
  description = "InfluxDB 관리자 비밀번호 (Secret Manager에 저장)"
  type        = string
  sensitive   = true
}

variable "influxdb_user" {
  description = "InfluxDB 사용자 이름"
  type        = string
  default     = "k6"
}

variable "influxdb_user_password" {
  description = "InfluxDB 사용자 비밀번호 (Secret Manager에 저장)"
  type        = string
  sensitive   = true
}
