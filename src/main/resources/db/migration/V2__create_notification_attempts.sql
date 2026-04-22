CREATE TYPE attempt_status AS ENUM (
    'SUCCESS',
    'FAILURE'
);

CREATE TABLE notification_attempts (
    id              UUID                     NOT NULL DEFAULT gen_random_uuid(),
    notification_id UUID                     NOT NULL,
    attempt_number  INT                      NOT NULL,
    status          attempt_status           NOT NULL,
    failure_reason  TEXT,
    attempted_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_by       VARCHAR(255),

    CONSTRAINT pk_notification_attempts PRIMARY KEY (id),
    CONSTRAINT fk_attempts_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id)
);
