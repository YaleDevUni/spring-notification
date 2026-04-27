INSERT INTO notification_templates (type, channel, subject_template, body_template) VALUES
('LECTURE_START',  'EMAIL',  '강의가 시작되었습니다',                '안녕하세요 {recipientId}님, {refType} ({refId}) 강의가 시작되었습니다.'),
('LECTURE_START',  'IN_APP', NULL,                                   '{refType} ({refId}) 강의가 시작되었습니다.'),
('EVENT_REMINDER', 'EMAIL',  '이벤트 일정 안내',                    '안녕하세요 {recipientId}님, {refType} ({refId}) 이벤트가 곧 시작됩니다.'),
('EVENT_REMINDER', 'IN_APP', NULL,                                   '{refType} ({refId}) 이벤트가 곧 시작됩니다.'),
('SYSTEM_ALERT',   'EMAIL',  '[시스템 알림] 공지사항이 있습니다',   '안녕하세요 {recipientId}님, 시스템 공지: {refType} ({refId})'),
('SYSTEM_ALERT',   'IN_APP', NULL,                                   '시스템 공지: {refType} ({refId})')
ON CONFLICT (type, channel) DO NOTHING;
