CREATE TABLE notification_templates (
    id               UUID                 NOT NULL DEFAULT gen_random_uuid(),
    type             notification_type    NOT NULL,
    channel          notification_channel NOT NULL,
    subject_template TEXT,
    body_template    TEXT                 NOT NULL,

    CONSTRAINT pk_notification_templates PRIMARY KEY (id),
    CONSTRAINT uq_templates_type_channel UNIQUE (type, channel)
);
