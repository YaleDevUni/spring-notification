CREATE TABLE notification_templates (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    type             VARCHAR(50)  NOT NULL,
    channel          VARCHAR(20)  NOT NULL,
    subject_template TEXT,
    body_template    TEXT         NOT NULL,

    CONSTRAINT pk_notification_templates PRIMARY KEY (id),
    CONSTRAINT uq_templates_type_channel UNIQUE (type, channel),
    CONSTRAINT chk_templates_type    CHECK (type    IN ('LECTURE_START','EVENT_REMINDER','SYSTEM_ALERT')),
    CONSTRAINT chk_templates_channel CHECK (channel IN ('EMAIL','IN_APP'))
);
