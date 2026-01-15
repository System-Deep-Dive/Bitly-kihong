#!/bin/bash
# PostgreSQL 백업 스크립트

# 백업 디렉토리 생성
mkdir -p backups

# 타임스탬프 생성
timestamp=$(date +"%Y%m%d-%H%M%S")
backup_file="backups/bitly-backup-$timestamp.sql"

echo "Starting backup to $backup_file..."

# pg_dump 실행 (호스트 포트를 통해 연결)
docker run --rm -e PGPASSWORD=password postgres:13 pg_dump -h host.docker.internal -p 5432 -U user -d bitly > "$backup_file"

if [ $? -eq 0 ]; then
    file_size=$(du -h "$backup_file" | cut -f1)
    echo "Backup completed successfully: $backup_file ($file_size)"
else
    echo "Backup failed" >&2
    exit 1
fi
