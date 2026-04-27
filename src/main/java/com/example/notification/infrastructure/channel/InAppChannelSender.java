package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.entity.NotificationTemplate;
import com.example.notification.infrastructure.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InAppChannelSender implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(InAppChannelSender.class);

    private final NotificationTemplateRepository templateRepository;

    public InAppChannelSender(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public void send(Notification notification) {
        Optional<NotificationTemplate> template =
                templateRepository.findByTypeAndChannel(notification.getType(), notification.getChannel());

        if (template.isPresent()) {
            String body = template.get().render(notification);
            log.info("[IN_APP] recipient={} body={}", notification.getRecipientId(), body);
        } else {
            log.info("[IN_APP] recipient={} type={} refId={} (no template)",
                    notification.getRecipientId(), notification.getType(), notification.getRefId());
        }
    }
}
