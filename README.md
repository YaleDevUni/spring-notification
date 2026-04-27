# 알림 발송 시스템

과제 C — 이벤트 / 비동기 / 운영 고려형

---

## 프로젝트 개요

수강 신청 완료, 강의 시작 D-1 등 다양한 이벤트에 대해 이메일 또는 인앱 알림을 발송하는 시스템입니다.

핵심 설계 목표:
- 알림 발송 실패가 비즈니스 트랜잭션에 영향을 주지 않음 (비동기 분리)
- 다중 인스턴스 환경에서도 중복 발송 없음 (DB 레벨 분산 락)
- 서버 재시작 또는 처리 중 장애 발생 시 미처리 알림 자동 복구
- 실제 메시지 브로커 없이 구현하되, 전환 가능한 구조 유지

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.0 |
| ORM | Spring Data JPA + Hibernate 6 |
| DB | PostgreSQL 16 |
| 마이그레이션 | Flyway 10 |
| 테스트 | JUnit 5, Mockito, Testcontainers |
| 인프라 | Docker Compose |

---

## 실행 방법

### 사전 요구사항
- Docker Desktop 실행 중
- Java 17+

### 1. 전체 실행 (앱 2대 + DB)

```bash
./run.sh
```

내부 순서: JAR 빌드 → Docker 이미지 빌드 → 컨테이너 시작 → 앱 기동 확인

| 서비스 | 주소 |
|--------|------|
| app1 | http://localhost:8080 |
| app2 | http://localhost:8081 |
| PostgreSQL | localhost:5433 |

### 2. DB만 올리고 로컬 실행

```bash
docker-compose up db
./gradlew bootRun
```

---

## 요구사항 해석 및 가정

### 해석

**"알림 처리 실패가 비즈니스 트랜잭션에 영향을 주어서는 안 된다"**
- API 요청 스레드는 DB INSERT(PENDING 상태 기록)까지만 수행하고 즉시 202를 반환합니다.
- 실제 발송은 별도 Worker 스레드가 담당하며, 발송 성패는 API 응답에 반영되지 않습니다.
- "예외를 무시하지 않는다"는 조건을 `notification_attempts`에 실패 사유를 기록하고, 재시도 및 DEAD 전락 정책으로 충족합니다.

**"중복 발송 방지"**
- DB UNIQUE 제약(`recipient_id + type + ref_type + ref_id + channel`)으로 동일 이벤트에 대한 중복 등록을 막습니다.
- 다중 인스턴스 동시 처리는 `FOR UPDATE SKIP LOCKED`로 방지합니다.
- `ON CONFLICT DO NOTHING`으로 좀비 복구 후 재처리 시 IN_APP 행 중복 INSERT도 안전하게 처리합니다.

**"실제 운영 환경으로 전환 가능한 구조"**
- `NotificationConsumer` 인터페이스와 `@Profile("db")`로 현재 DB 폴링 구현체를 격리했습니다.
- `@Profile("kafka")`로 Kafka Consumer 구현체를 추가하고 `spring.profiles.active=kafka`로 전환하면 됩니다.
- Worker 내부 처리 로직(`processNotification`)은 채널과 무관하게 재사용됩니다.

### 가정

- `recipient_id`는 외부 시스템(회원 서비스)의 식별자이므로 별도 검증 없이 String으로 수신합니다.
- 실제 이메일 발송은 구현 범위 외이므로 로그 출력으로 대체합니다.
- IN_APP 채널의 `read` 필터(`?read=true/false`)는 IN_APP 알림에만 의미가 있으므로, EMAIL 알림은 해당 필터 결과에서 제외됩니다.

---

## 설계 결정과 이유

### 비동기 처리 구조: DB 폴링

```
POST /notifications
    └─ DB INSERT (PENDING)          ← API 스레드 (동기)
           │
           └─ DbPollingConsumer      ← Worker 스레드 (비동기)
                  │  scheduleWithFixedDelay (5초)
                  ├─ FOR UPDATE SKIP LOCKED  → PROCESSING
                  ├─ NotificationProcessor.process()
                  └─ SENT / PENDING(재시도) / DEAD
```

메시지 브로커 없이 DB를 큐로 사용하는 Transactional Outbox 변형 패턴입니다.
장점: 외부 인프라 의존 없음, DB 트랜잭션과 동일한 내구성 보장, 서버 재시작 후 자동 복구.

### 중복 처리 방지: 두 겹 방어

1. **DB UNIQUE 제약** — 동일 이벤트 등록 자체를 차단 (409 Conflict 반환)
2. **FOR UPDATE SKIP LOCKED** — 다중 인스턴스가 동시에 같은 알림을 폴링할 때 한 인스턴스만 처리

### 좀비 복구: RecoveryScheduler

PROCESSING 상태에서 서버가 죽으면 해당 알림은 영원히 처리되지 않습니다.
`notification_locks` 테이블에 TTL(`expires_at`)을 두고, 60초마다 만료된 락을 삭제하며 PENDING으로 복귀시킵니다.

```
locks.expires_at < NOW()  →  DELETE lock  →  UPDATE status: PROCESSING → PENDING
```

### 재시도 정책

```
자동 재시도 (max-attempts: 3)
  PENDING → PROCESSING → 실패 → PENDING (1회, 2회)
                               → DEAD   (3회 소진)

수동 재시도 (manual-max-attempts: 2)
  POST /{id}/retry → PENDING  (최대 2회 추가 허용)
```

- 실패 이력은 초기화하지 않습니다. 실패 패턴 분석 및 감사 목적으로 누적 보존합니다.
- 수동 재시도 한도: 자동 재시도 소진 후 추가 2회를 허용해 운영자가 일시적 장애를 직접 복구할 수 있습니다.

### sentAt 기록: native query

JPQL은 `NOW()` 함수를 지원하지 않습니다.
`SET status='SENT', sent_at=NOW()`를 원자적으로 처리하기 위해 native query `markSent()`를 별도로 작성했습니다.
앱 서버 시각(`Instant.now()`) 대신 DB 서버 시각을 사용해 다중 인스턴스 간 시계 오차를 제거합니다.

### 읽음 처리 멱등성

`UPDATE ... WHERE read_at IS NULL`로 최초 1회만 반영되도록 DB 레벨에서 보장합니다.
- 첫 번째 호출: `200 OK` (변경 발생)
- 이후 호출: `204 No Content` (no-op)

---

## API 목록 및 예시

### POST /notifications — 알림 등록

```bash
curl -X POST http://localhost:8080/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "recipientId": "user-001",
    "type": "LECTURE_START",
    "channel": "EMAIL",
    "refType": "LECTURE",
    "refId": "lecture-42"
  }'
```

| 필드 | 필수 | 설명 |
|------|------|------|
| recipientId | Y | 수신자 ID |
| type | Y | `LECTURE_START` / `EVENT_REMINDER` / `SYSTEM_ALERT` |
| channel | Y | `EMAIL` / `IN_APP` |
| refType | Y | 참조 엔티티 타입 (예: `LECTURE`, `EVENT`) |
| refId | Y | 참조 엔티티 ID |
| scheduledAt | N | 예약 발송 시각 (ISO 8601, 생략 시 즉시 처리 대상) |

**응답** `202 Accepted`
```json
{
  "id": "bb99c225-093c-4fde-8102-d1cc0d62397a",
  "recipientId": "user-001",
  "type": "LECTURE_START",
  "channel": "EMAIL",
  "status": "PENDING",
  "scheduledAt": null,
  "sentAt": null,
  "createdAt": "2026-04-27T08:56:37Z",
  "read": null
}
```

| 상태 코드 | 조건 |
|-----------|------|
| 202 | 정상 접수 |
| 400 | 필수 필드 누락 |
| 409 | 동일 이벤트 중복 등록 |

---

### GET /notifications/{id} — 개별 조회

```bash
curl http://localhost:8080/notifications/bb99c225-093c-4fde-8102-d1cc0d62397a
```

**응답** `200 OK`
```json
{
  "id": "bb99c225-093c-4fde-8102-d1cc0d62397a",
  "status": "SENT",
  "sentAt": "2026-04-27T08:56:42Z",
  "read": null
}
```

| 상태 코드 | 조건 |
|-----------|------|
| 200 | 존재하는 ID |
| 404 | 존재하지 않는 ID |

---

### GET /notifications?recipientId={id} — 목록 조회

```bash
# 전체 목록
curl "http://localhost:8080/notifications?recipientId=user-001"

# 채널 필터
curl "http://localhost:8080/notifications?recipientId=user-001&channel=IN_APP"

# 미읽음만 (channel=IN_APP 필수)
curl "http://localhost:8080/notifications?recipientId=user-001&channel=IN_APP&read=false"

# 읽음만
curl "http://localhost:8080/notifications?recipientId=user-001&channel=IN_APP&read=true"
```

**응답** `200 OK` — 배열.

| 상태 코드 | 조건 |
|-----------|------|
| 200 | 정상 조회 |
| 400 | `read` 필터를 `IN_APP` 외 채널에 사용한 경우 |

---

### PATCH /notifications/{id}/in-app/read — 읽음 처리 (IN_APP 전용)

```bash
curl -X PATCH http://localhost:8080/notifications/bd125b82-.../in-app/read
```

| 상태 코드 | 조건 |
|-----------|------|
| 200 | 최초 읽음 처리 성공 |
| 204 | 이미 읽은 상태 (no-op) |

---

### POST /notifications/{id}/retry — 수동 재시도

```bash
curl -X POST http://localhost:8080/notifications/bb99c225-.../retry
```

| 상태 코드 | 조건 |
|-----------|------|
| 200 | DEAD → PENDING 전환 성공 |
| 409 | DEAD가 아닌 상태이거나 수동 재시도 한도 초과 |
| 404 | 존재하지 않는 ID |

---

## 데이터 모델 설명

```
notifications (핵심 테이블)
├── id, recipient_id, type, channel, ref_type, ref_id
├── status: PENDING → PROCESSING → SENT / DEAD
├── scheduled_at: NULL이면 즉시 처리 대상
├── sent_at: 발송 완료 시각 (DB NOW())
└── UNIQUE(recipient_id, type, ref_type, ref_id, channel) ← 중복 방지

notification_attempts (발송 이력)
├── notification_id (FK)
├── attempt_number, status (SUCCESS / FAILURE)
├── failure_reason: 실패 시 예외 메시지 기록
└── locked_by: 처리한 인스턴스 ID

notification_locks (분산 락)
├── notification_id (PK, FK)
├── locked_by: 인스턴스 ID
└── expires_at: TTL (기본 60초), 만료 시 좀비 복구 대상

in_app_notifications (IN_APP 전용)
├── notification_id (PK, FK)
└── read_at: NULL이면 미읽음, NOT NULL이면 읽은 시각

notification_templates (선택 구현)
├── type + channel (UNIQUE)
├── subject_template
└── body_template
```

### 알림 상태 전이

```
PENDING
  └─(Worker 획득)─→ PROCESSING
                       ├─(성공)─────→ SENT         (종료)
                       ├─(실패, 재시도 남음)─→ PENDING  (자동 재시도)
                       └─(실패, 한도 소진)─→ DEAD
                                               └─(수동 retry)─→ PENDING
```

---

## 테스트 실행 방법

### 사전 조건

테스트용 DB가 필요합니다.

```bash
docker-compose up db
```

`notification_test` DB를 별도로 생성합니다.

```bash
docker exec -it spring-notification-db-1 \
  psql -U user -c "CREATE DATABASE notification_test;"
```

### 전체 테스트 실행

```bash
./gradlew test
```

### 테스트 구성

| 테스트 클래스 | 종류 | 설명 |
|---------------|------|------|
| `NotificationTest` | 단위 | 엔티티 상태 전이 |
| `InAppNotificationTest` | 단위 | 읽음 처리 |
| `NotificationProcessorTest` | 단위 | 채널별 분기 처리 |
| `ChannelSenderTest` | 단위 | 이메일/인앱 발송 로그 |
| `NotificationServiceTest` | 단위 | 서비스 계층 (Mockito) |
| `DbPollingConsumerTest` | 단위 | 폴링/처리 로직 (Mockito) |
| `RecoverySchedulerTest` | 단위 | 좀비 복구 로직 |
| `NotificationControllerTest` | 단위 | 컨트롤러 (MockMvc) |
| `NotificationRepositoryTest` | 통합 | JPA 쿼리 검증 (실 PostgreSQL) |
| `NotificationApiIntegrationTest` | 통합 | 전체 스택 E2E (실 PostgreSQL) |

---

## 미구현 / 제약사항

| 항목 | 상태 | 비고 |
|------|------|------|
| 실제 이메일 발송 | 미구현 | 로그 출력으로 대체 |
| 알림 템플릿 | 스키마만 구현 | `notification_templates` 테이블과 조회 메서드까지 작성, 발송 시 실제 적용은 미완성 |
| Kafka 전환 | 미구현 | `NotificationConsumer` 인터페이스로 구조만 준비 |
| 인증/인가 | 미구현 | 수신자 ID 검증 없음 |
| 페이지네이션 | 미구현 | 목록 조회 시 전체 반환 |
| 에러 응답 body | 미구현 | 상태 코드만 반환, 메시지 없음 |

---

## AI 활용 범위

본 프로젝트는 Claude Code (claude-sonnet-4-6)를 활용해 개발했습니다.

### AI가 주도한 영역

- 초기 프로젝트 스캐폴딩 및 디렉토리 구조 설계
- Flyway 마이그레이션 SQL 작성
- 테스트 코드 작성 (단위 / 통합 / Repository)
- `DbPollingConsumer` 폴링 로직 구현
- `RecoveryScheduler` 좀비 복구 구현
- `FOR UPDATE SKIP LOCKED`, `ON CONFLICT DO NOTHING` 등 PostgreSQL 전용 쿼리 작성

### 사람이 주도하고 AI가 보조한 영역

- 전체 설계 방향 결정 (DB 폴링 vs 메시지 브로커, 상태 머신 설계)
- 재시도 정책 결정 (자동 3회 + 수동 2회, 이력 초기화 없음)
- 중복 방지 전략 결정 (UNIQUE 제약 + SKIP LOCKED 이중 방어)
- 코드 리뷰 및 방향 수정 (OSIV 비활성화, DTO 도입, sentAt 버그 발견)

### AI 활용 방식

- Claude Code CLI를 통해 TDD 사이클(Red → Green → Refactor)을 함께 진행
- 각 기능 구현 전 요구사항을 함께 분석하고 설계 방향을 논의
- 커밋 단위는 사람이 직접 판단하여 수행
