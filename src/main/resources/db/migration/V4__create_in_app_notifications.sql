CREATE TABLE in_app_notifications (
    notification_id UUID                     NOT NULL,
    read_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_in_app_notifications PRIMARY KEY (notification_id),
    CONSTRAINT fk_in_app_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id)
);
