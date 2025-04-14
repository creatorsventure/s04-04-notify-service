package com.cv.s0404notifyservice.service.implementation;

import com.cv.s0402notifyservicepojo.constant.NofityConstant;
import com.cv.s0402notifyservicepojo.dto.MessageDto;
import com.cv.s0402notifyservicepojo.dto.RecipientDto;
import com.cv.s0402notifyservicepojo.enm.DeliveryTemplate;
import com.cv.s0402notifyservicepojo.entity.DeliveryHistory;
import com.cv.s0402notifyservicepojo.entity.Message;
import com.cv.s0404notifyservice.repository.DeliveryHistoryRepository;
import com.cv.s0404notifyservice.repository.MessageRepository;
import com.cv.s0404notifyservice.repository.RecipientRepository;
import com.cv.s0404notifyservice.service.intrface.EmailService;
import com.cv.s0404notifyservice.service.mapper.MessageMapper;
import com.cv.s0404notifyservice.service.mapper.RecipientMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@AllArgsConstructor
@Service
public class EmailServiceImplementation implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private MessageSource messageSource;
    private MessageRepository messageRepository;
    private MessageMapper messageMapper;
    private RecipientRepository recipientRepository;
    private RecipientMapper recipientMapper;
    private DeliveryHistoryRepository deliveryHistoryRepository;

    private record EmailPayload(MimeMessage message, MimeMessageHelper helper) {
    }

    @Async(NofityConstant.NOTIFY_TASK_EXECUTOR) // 💡 runs in a virtual thread pool
    public CompletableFuture<Void> sendEmail(MessageDto message, RecipientDto recipient) {
        return CompletableFuture.supplyAsync(() -> getContextSafe(message, recipient))
                .thenApplyAsync(ctx -> renderTemplate(ctx, message, recipient))
                .thenApplyAsync(emailPayload -> handleAttachmentIfRequired(emailPayload, message))
                .thenAcceptAsync(emailPayload -> sendMimeEmail(message, recipient, emailPayload))
                .exceptionally(e -> {
                    log.error("❌ Failed to send email to {}: {}", recipient.getEmail(), ExceptionUtils.getStackTrace(e));
                    return null;
                });
    }

    private Context getContextSafe(MessageDto message, RecipientDto recipient) {
        try {
            Context context = new Context(message.getLocale());
            if (message.getDeliveryTemplate().equals(DeliveryTemplate.BASE_LAYOUT)) {
                message.setSubject(message.getSubjectDto().isTranslate()
                        ? messageSource.getMessage(message.getSubjectDto().getKeyOrContent(), null, message.getLocale())
                        : message.getSubjectDto().getKeyOrContent());
                context.setVariable("userName", recipient.getName());
                context.setVariable("subject", message.getSubject());
                context.setVariable("trackId", message.getTrackId());
                context.setVariable("contentLines", message.getContentLines());
                context.setVariable("tableDto", message.getTableDto());
            } else {
                log.error("❌ Invalid delivery template for {}", recipient.getEmail());
                throw new RuntimeException("Invalid delivery template");
            }
            return context;
        } catch (Exception e) {
            log.error("❌ Failed to construct context for {}: {}", recipient.getEmail(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    private EmailPayload renderTemplate(Context context, MessageDto message, RecipientDto recipient) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            log.info("Rendering email template: {}", message.getDeliveryTemplate().toString());
            message.setContent(templateEngine.process("layout/" + message.getDeliveryTemplate().toString(), context));
            helper.setText(message.getContent(), true);
            return new EmailPayload(mimeMessage, helper);
        } catch (Exception e) {
            log.error("❌ Template processing failed for {}: {}", recipient.getEmail(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    public EmailPayload handleAttachmentIfRequired(EmailPayload emailPayload, MessageDto message) {
        if (!message.isAttachment() || StringUtils.hasText(message.getAttachmentPath())) return emailPayload;
        File zipFile;
        try {
            zipFile = zipFolder(message.getAttachmentPath());
            emailPayload.helper.addAttachment("attachments.zip", zipFile);
        } catch (Exception e) {
            log.error("❌ Failed to attach files {}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to attach files: " + e.getMessage(), e);
        }
        return emailPayload;
    }

    private File zipFolder(String folderPath) throws Exception {
        Path sourceFolder = Paths.get(folderPath);
        if (!Files.isDirectory(sourceFolder)) throw new IllegalArgumentException("Path is not a directory");

        File zipFile = Files.createTempFile("attachments-", ".zip").toFile();

        try (
                FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos)
        ) {
            Files.walk(sourceFolder)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        Path relativePath = sourceFolder.relativize(file);
                        try (InputStream fis = Files.newInputStream(file)) {
                            zos.putNextEntry(new ZipEntry(relativePath.toString()));
                            fis.transferTo(zos);
                            zos.closeEntry();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to zip file: " + file, e);
                        }
                    });
        }
        return zipFile;
    }

    private void sendMimeEmail(MessageDto message, RecipientDto recipient, EmailPayload emailPayload) {
        Message messageEntity = null;
        DeliveryHistory deliveryHistory = new DeliveryHistory();
        try {
            deliveryHistory.setStatus(false);
            messageEntity = messageMapper.toEntity(message);
            messageEntity.setId(null);
            emailPayload.helper.setTo(recipient.getEmail());
            Optional.ofNullable(recipient.getCcList())
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(cc -> {
                        try {
                            emailPayload.helper.addCc(cc);
                        } catch (MessagingException e) {
                            log.warn("Invalid CC skipped: {} - {}", cc, e.getMessage());
                        }
                    });
            Optional.ofNullable(recipient.getBccList())
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(bcc -> {
                        try {
                            emailPayload.helper.addBcc(bcc);
                        } catch (MessagingException e) {
                            log.warn("Invalid CC skipped: {} - {}", bcc, e.getMessage());
                        }
                    });

            emailPayload.helper.setSubject(message.getSubject());
            messageEntity.setContent(message.getContent());
            mailSender.send(emailPayload.message);
            deliveryHistory.setStatus(true);
            deliveryHistory.setDeliveryDetails("Email sent successfully");
            log.info("✅ Email sent to {} [template: {}]", recipient.getEmail(), message.getDeliveryTemplate());
        } catch (Exception e) {
            log.error("❌ Mime message creation or sending failed for {}: {}", recipient.getEmail(), e.getMessage());
            deliveryHistory.setStatus(false);
            deliveryHistory.setDeliveryDetails(ExceptionUtils.getStackTrace(e));
        }
        if (deliveryHistory.isStatus() && messageEntity != null) {
            try {
                var recipientEntity = recipientMapper.toEntity(recipient);
                recipientEntity.setId(null);
                recipientEntity = recipientRepository.save(recipientEntity);
                messageEntity.setRecipient(recipientEntity);
                messageEntity = messageRepository.save(messageEntity);
                deliveryHistory.setMessage(messageEntity);
                deliveryHistoryRepository.save(deliveryHistory);
            } catch (Exception e) {
                log.error("❌ Status persisting failed for {}: {}", recipient.getEmail(), ExceptionUtils.getStackTrace(e));
                throw new RuntimeException(e);
            }
        } else {
            log.error("❌ Message entity is null {}", recipient.getEmail());
            throw new RuntimeException("Message entity is null");
        }
    }

}
