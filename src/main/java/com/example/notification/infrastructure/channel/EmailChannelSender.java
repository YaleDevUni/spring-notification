package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.entity.NotificationTemplate;
import com.example.notification.infrastructure.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EmailChannelSender implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

    private final NotificationTemplateRepository templateRepository;

    public EmailChannelSender(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public void send(Notification notification) {
        Optional<NotificationTemplate> template =
                templateRepository.findByTypeAndChannel(notification.getType(), notification.getChannel());

        if (template.isPresent()) {
            String body = template.get().render(notification);
            String subject = template.get().getSubjectTemplate();
            log.info("[EMAIL] recipient={} subject={} body={}", notification.getRecipientId(), subject, body);
        } else {
            log.info("[EMAIL] recipient={} type={} refId={} (no template)",
                    notification.getRecipientId(), notification.getType(), notification.getRefId());
        }
    }
}
