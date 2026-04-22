CREATE TYPE notification_type AS ENUM (
    'LECTURE_START',
    'EVENT_REMINDER',
    'SYSTEM_ALERT'
);

CREATE TYPE notification_channel AS ENUM (
    'EMAIL',
    'IN_APP'
);

CREATE TYPE notification_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'SENT',
    'FAILED',
    'DEAD'
);

CREATE TABLE notifications (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid(),
    recipient_id  VARCHAR(255)             NOT NULL,
    type          notification_type        NOT NULL,
    channel       notification_channel     NOT NULL,
    ref_type      VARCHAR(100)             NOT NULL,
    ref_id        VARCHAR(255)             NOT NULL,
    status        notification_status      NOT NULL DEFAULT 'PENDING',
    scheduled_at  TIMESTAMP WITH TIME ZONE,
    sent_at       TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uq_notifications_dedup UNIQUE (recipient_id, type, ref_type, ref_id, channel)
);
