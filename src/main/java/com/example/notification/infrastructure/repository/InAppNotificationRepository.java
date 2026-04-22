package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE in_app_notifications
            SET read_at = NOW()
            WHERE notification_id = :id
            AND read_at IS NULL
            """, nativeQuery = true)
    int markReadById(@Param("id") UUID notificationId);
}
