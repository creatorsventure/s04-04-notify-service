package com.cv.s0404notifyservice.service.component;

import com.cv.s0402notifyservicepojo.dto.MessageDto;
import com.cv.s0402notifyservicepojo.dto.NotifyDto;
import com.cv.s0402notifyservicepojo.dto.NotifyMapDto;
import com.cv.s0402notifyservicepojo.dto.RecipientDto;
import com.cv.s0402notifyservicepojo.enm.DeliveryChannel;
import com.cv.s0404notifyservice.service.intrface.EmailService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
public class NotifyListener {

    private EmailService emailService;

    @KafkaListener(topics = "${app.notify-service.kafka.topic}", groupId = "${app.notify-service.kafka.group}", containerFactory = "notifyDtoListenerFactory")
    public void listen(NotifyDto dto, Acknowledgment ack) {
        if (dto == null || dto.getMappings() == null || dto.getMappings().isEmpty()) {
            log.warn("🚫 Received null or empty NotifyDto.");
            ack.acknowledge();
            return;
        }
        try {
            log.info("Received message: {}", dto);
            for (NotifyMapDto map : dto.getMappings()) {
                MessageDto message = dto.getMessages().get(map.getMessageId());
                if (message == null) {
                    log.error("Skipping mapping: message not found for ID {}", map.getMessageId());
                    continue;
                }
                for (String recipientId : map.getRecipientIds()) {
                    RecipientDto recipient = dto.getRecipients().get(recipientId);
                    if (recipient == null) {
                        log.error("Skipping mapping: recipient not found for ID {}", recipientId);
                        continue;
                    }
                    if (message.getDeliveryChannel().equals(DeliveryChannel.EMAIL)) {
                        emailService.sendEmail(message, recipient);
                    } else if (message.getDeliveryChannel().equals(DeliveryChannel.PHONE)) {
                        log.error("Skipping mapping: delivery mechanism not implemented {}: {}", recipientId, message.getDeliveryChannel());
                    } else {
                        log.error("Skipping mapping: invalid delivery mechanism {}: {}", recipientId, message.getDeliveryChannel());
                    }
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("NotifyListener.listen {}", ExceptionUtils.getStackTrace(e));
        }
    }
}
