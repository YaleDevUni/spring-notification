#!/usr/bin/env bash
set -e

echo "=== 1. JAR 빌드 ==="
./gradlew bootJar

echo "=== 2. Docker 이미지 빌드 및 컨테이너 시작 ==="
docker-compose up --build -d

echo "=== 3. 앱 기동 대기 ==="
until curl -sf http://localhost:8080/notifications?recipientId=health-check > /dev/null 2>&1; do
  printf '.'
  sleep 2
done

echo ""
echo "=== 완료 ==="
echo "  app1 : http://localhost:8080"
echo "  app2 : http://localhost:8081"
echo "  DB   : localhost:5433"
