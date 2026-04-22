CREATE TABLE notification_locks (
    notification_id UUID                     NOT NULL,
    locked_by       VARCHAR(255)             NOT NULL,
    locked_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_notification_locks PRIMARY KEY (notification_id),
    CONSTRAINT fk_locks_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id)
);
