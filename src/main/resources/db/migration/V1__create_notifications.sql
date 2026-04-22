CREATE TABLE notifications (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    recipient_id  VARCHAR(255)             NOT NULL,
    type          VARCHAR(50)              NOT NULL,
    channel       VARCHAR(20)              NOT NULL,
    ref_type      VARCHAR(100)             NOT NULL,
    ref_id        VARCHAR(255)             NOT NULL,
    status        VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    scheduled_at  TIMESTAMP WITH TIME ZONE,
    sent_at       TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uq_notifications_dedup UNIQUE (recipient_id, type, ref_type, ref_id, channel),
    CONSTRAINT chk_notifications_type   CHECK (type   IN ('LECTURE_START','EVENT_REMINDER','SYSTEM_ALERT')),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL','IN_APP')),
    CONSTRAINT chk_notifications_status  CHECK (status  IN ('PENDING','PROCESSING','SENT','FAILED','DEAD'))
);
