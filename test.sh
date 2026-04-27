#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────
# 색상
# ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

DB_PORT=5433
DB_USER=user
DB_PASSWORD=password
MAIN_DB=notification
TEST_DB=notification_test

# ─────────────────────────────────────────────────────────────────
# 유틸
# ─────────────────────────────────────────────────────────────────
info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail()    { echo -e "${RED}[FAIL]${NC} $*" >&2; }
header()  { echo -e "\n${BOLD}${BLUE}━━━ $* ━━━${NC}\n"; }

psql_exec() {
    # $1: database, $2: SQL
    PGPASSWORD=$DB_PASSWORD psql -h localhost -p $DB_PORT -U $DB_USER -d "$1" -tAc "$2" 2>/dev/null
}

# ─────────────────────────────────────────────────────────────────
# DB 자동 셋업
# ─────────────────────────────────────────────────────────────────
setup_test_db() {
    header "테스트 DB 환경 설정"

    # 1. Docker 컨테이너 확인
    if ! docker compose ps --services --filter status=running 2>/dev/null | grep -q '^db$'; then
        info "PostgreSQL 컨테이너가 중지 상태입니다. 시작합니다..."
        docker compose up -d db

        echo -n "  DB 기동 대기 중"
        until docker compose exec -T db pg_isready -U $DB_USER -q 2>/dev/null; do
            printf '.'; sleep 2
        done
        echo ""
    else
        success "PostgreSQL 컨테이너 실행 중"
    fi

    # 2. psql 클라이언트 확인
    if ! command -v psql &>/dev/null; then
        warn "psql 클라이언트가 없습니다. 컨테이너 내부 psql을 사용합니다."
        psql_exec() {
            docker compose exec -T db psql -U $DB_USER -d "$1" -tAc "$2" 2>/dev/null
        }
    fi

    # 3. notification_test DB 생성 (없으면)
    EXISTS=$(psql_exec "postgres" "SELECT 1 FROM pg_database WHERE datname='$TEST_DB'" || echo "")
    if [ "$EXISTS" = "1" ]; then
        success "notification_test DB 이미 존재함"
    else
        info "notification_test DB 생성 중..."
        psql_exec "postgres" "CREATE DATABASE $TEST_DB OWNER $DB_USER" > /dev/null
        success "notification_test DB 생성 완료"
    fi

    # 4. 스키마 동기화: main DB의 마이그레이션을 test DB에 적용
    #    (Flyway가 Spring Boot 테스트 시동 시 자동으로 처리하지만,
    #     혹시 flyway_schema_history 테이블이 깨진 경우를 대비해 확인)
    FLYWAY_OK=$(psql_exec "$TEST_DB" \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='flyway_schema_history'" || echo "0")
    if [ "$FLYWAY_OK" = "0" ]; then
        warn "Flyway 히스토리가 없습니다 — 첫 테스트 실행 시 Flyway가 스키마를 자동 생성합니다."
    else
        MAIN_VER=$(psql_exec "$MAIN_DB"  "SELECT MAX(version) FROM flyway_schema_history" 2>/dev/null || echo "?")
        TEST_VER=$(psql_exec "$TEST_DB"  "SELECT MAX(version) FROM flyway_schema_history" 2>/dev/null || echo "?")
        if [ "$MAIN_VER" = "$TEST_VER" ]; then
            success "스키마 버전 동기화됨 (V${MAIN_VER})"
        else
            warn "main=V${MAIN_VER}, test=V${TEST_VER} — 버전 차이 있음 (테스트 시 Flyway가 자동 적용)"
        fi
    fi

    echo ""
    success "테스트 DB 환경 준비 완료"
}

# ─────────────────────────────────────────────────────────────────
# 테스트 실행 함수
# ─────────────────────────────────────────────────────────────────
run_tests() {
    # $@: Gradle --tests 패턴들 (여러 개 가능)
    local args=()
    for pattern in "$@"; do
        args+=(--tests "$pattern")
    done
    echo ""
    info "실행: ./gradlew test ${args[*]}"
    echo ""
    ./gradlew test "${args[@]}" --rerun-tasks 2>&1 | grep -E \
        '(BUILD|PASSED|FAILED|ERROR|tests|Task :test|> Task|Caused by|\.java)' \
        || true
    # 원본 exit code 보존
    local rc=${PIPESTATUS[0]}
    echo ""
    if [ $rc -eq 0 ]; then
        success "테스트 완료 — 모두 통과"
    else
        fail "테스트 실패 — 자세한 내용: build/reports/tests/test/index.html"
    fi
    return $rc
}

run_all() {
    echo ""
    info "실행: ./gradlew test"
    echo ""
    ./gradlew test --rerun-tasks
    local rc=$?
    if [ $rc -eq 0 ]; then success "전체 테스트 완료 — 모두 통과"
    else fail "테스트 실패 — build/reports/tests/test/index.html 확인"; fi
    return $rc
}

# ─────────────────────────────────────────────────────────────────
# 메뉴
# ─────────────────────────────────────────────────────────────────
print_menu() {
    echo -e "${BOLD}${BLUE}"
    echo "  ┌─────────────────────────────────────────────────────┐"
    echo "  │             notification 테스트 선택 메뉴            │"
    echo "  └─────────────────────────────────────────────────────┘${NC}"
    echo ""
    echo -e "  ${BOLD}[ 단위 테스트 — DB 불필요 ]${NC}"
    echo "  1) 도메인 엔티티       NotificationTest, InAppNotificationTest, NotificationTemplateTest"
    echo "  2) 서비스 레이어       NotificationServiceTest"
    echo "  3) 인프라 레이어       ChannelSenderTest, DbPollingConsumerTest, RecoverySchedulerTest"
    echo "  4) 프레젠테이션        NotificationControllerTest (MockMvc + Mock Service)"
    echo "  5) 도메인 프로세서     NotificationProcessorTest"
    echo ""
    echo -e "  ${BOLD}[ 통합 테스트 — PostgreSQL notification_test DB 필요 ]${NC}"
    echo "  6) Repository 통합     NotificationRepositoryTest  (SKIP LOCKED, RETURNING 검증)"
    echo "  7) API E2E 통합        NotificationApiIntegrationTest  (HTTP → Service → DB 전 흐름)"
    echo ""
    echo -e "  ${BOLD}[ 묶음 실행 ]${NC}"
    echo "  8) 단위 테스트 전체    (1 ~ 5)"
    echo "  9) 통합 테스트 전체    (6 ~ 7)  ※ DB 셋업 포함"
    echo "  0) 전체 테스트         (1 ~ 7)  ※ DB 셋업 포함"
    echo ""
    echo "  q) 종료"
    echo ""
}

# ─────────────────────────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${BOLD}${CYAN}Spring Notification — 테스트 자동화 스크립트${NC}"
    echo ""

    # --setup 플래그만 전달하면 DB 셋업 후 종료
    if [[ "${1:-}" == "--setup" ]]; then
        setup_test_db
        exit 0
    fi

    # 인자로 번호를 직접 넘긴 경우 (CI 등에서 비대화형 실행)
    if [[ "${1:-}" =~ ^[0-9q]$ ]]; then
        CHOICE="${1}"
    else
        print_menu
        read -rp "$(echo -e "  ${BOLD}번호를 입력하세요 [0-9/q]:${NC} ")" CHOICE
    fi

    echo ""
    case "$CHOICE" in
        1)
            run_tests \
                "com.example.notification.domain.entity.*" \
                "com.example.notification.domain.enums.*"
            ;;
        2)
            run_tests "com.example.notification.application.service.*"
            ;;
        3)
            run_tests \
                "com.example.notification.infrastructure.channel.*" \
                "com.example.notification.infrastructure.consumer.*" \
                "com.example.notification.infrastructure.scheduler.*"
            ;;
        4)
            run_tests "com.example.notification.presentation.*"
            ;;
        5)
            run_tests "com.example.notification.domain.processor.*"
            ;;
        6)
            setup_test_db
            run_tests "com.example.notification.infrastructure.repository.*"
            ;;
        7)
            setup_test_db
            run_tests "com.example.notification.NotificationApiIntegrationTest"
            ;;
        8)
            run_tests \
                "com.example.notification.domain.entity.*" \
                "com.example.notification.domain.enums.*" \
                "com.example.notification.domain.processor.*" \
                "com.example.notification.application.service.*" \
                "com.example.notification.infrastructure.channel.*" \
                "com.example.notification.infrastructure.consumer.*" \
                "com.example.notification.infrastructure.scheduler.*" \
                "com.example.notification.presentation.*"
            ;;
        9)
            setup_test_db
            run_tests \
                "com.example.notification.infrastructure.repository.*" \
                "com.example.notification.NotificationApiIntegrationTest"
            ;;
        0)
            setup_test_db
            run_all
            ;;
        q|Q)
            echo "종료합니다."
            exit 0
            ;;
        *)
            fail "알 수 없는 선택: '$CHOICE'"
            print_menu
            exit 1
            ;;
    esac
}

main "$@"
