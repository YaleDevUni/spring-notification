package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.NotificationLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationLockRepository extends JpaRepository<NotificationLock, UUID> {

    @Modifying
    @Query(value = """
            DELETE FROM notification_locks
            WHERE expires_at < NOW()
            RETURNING notification_id
            """, nativeQuery = true)
    List<UUID> deleteExpiredLocksReturningIds();
}
