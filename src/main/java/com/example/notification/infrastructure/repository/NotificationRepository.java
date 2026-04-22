package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // JPQL은 FOR UPDATE SKIP LOCKED를 지원하지 않으므로 nativeQuery 필수
    // SKIP LOCKED: 다른 인스턴스/워커가 락 잡은 row를 건너뜀 → 별도 분산 락 없이 중복 처리 방지
    // LIMIT :limit = workerPool.coreSize: 처리 가능한 양만큼만 폴링해 큐 적체 방지
    @Query(value = """
            SELECT * FROM notifications
            WHERE status = 'PENDING'
            AND (scheduled_at IS NULL OR scheduled_at <= NOW())
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Notification> findPendingForUpdate(@Param("limit") int limit);

    // open-in-view=false 환경에서 LAZY inAppNotification 직렬화 시 LazyInitializationException 방지
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.inAppNotification WHERE n.id = :id")
    Optional<Notification> findByIdWithInApp(@Param("id") UUID id);

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

    // clearAutomatically=true: 벌크 UPDATE 후 1차 캐시(영속성 컨텍스트)를 비워
    //   이후 findById 등이 캐시된 구 상태 대신 실제 DB 값을 읽도록 강제
    // CAS 패턴: currentStatus 불일치 시 영향 row = 0 반환 → 다른 Worker가 이미 처리했음을 감지
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
