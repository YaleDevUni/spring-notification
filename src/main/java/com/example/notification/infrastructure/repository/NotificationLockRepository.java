package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.NotificationLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationLockRepository extends JpaRepository<NotificationLock, UUID> {

    // RETURNING: PostgreSQL 전용. DELETE와 ID 반환을 한 쿼리로 처리 — MySQL이었다면 SELECT 후 DELETE 두 번 필요
    // @Modifying: DML 쿼리임을 명시. clearAutomatically 생략 가능 — 삭제된 row는 어차피 캐시에서 의미 없음
    @Modifying
    @Query(value = """
            DELETE FROM notification_locks
            WHERE expires_at < NOW()
            RETURNING notification_id
            """, nativeQuery = true)
    List<UUID> deleteExpiredLocksReturningIds();
}
