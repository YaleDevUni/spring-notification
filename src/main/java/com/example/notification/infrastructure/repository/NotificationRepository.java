package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(value = """
            SELECT * FROM notifications
            WHERE status = 'PENDING'
            AND (scheduled_at IS NULL OR scheduled_at <= NOW())
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> findPendingForUpdate(@Param("limit") int limit);

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.inAppNotification
            WHERE n.recipientId = :recipientId
            ORDER BY n.createdAt DESC
            """)
    List<Notification> findByRecipientId(@Param("recipientId") String recipientId);

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.inAppNotification i
            WHERE n.recipientId = :recipientId
            AND n.channel = :channel
            AND i.readAt IS NULL
            ORDER BY n.createdAt DESC
            """)
    List<Notification> findUnreadByRecipientId(@Param("recipientId") String recipientId,
                                               @Param("channel") NotificationChannel channel);

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.inAppNotification i
            WHERE n.recipientId = :recipientId
            AND n.channel = :channel
            AND i.readAt IS NOT NULL
            ORDER BY n.createdAt DESC
            """)
    List<Notification> findReadByRecipientId(@Param("recipientId") String recipientId,
                                             @Param("channel") NotificationChannel channel);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.status = :status
            WHERE n.id = :id AND n.status = :currentStatus
            """)
    int updateStatusIfMatch(@Param("id") UUID id,
                            @Param("currentStatus") NotificationStatus currentStatus,
                            @Param("status") NotificationStatus status);
}
