package tn.iteam.notification.channels;

import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import tn.iteam.notification.NotificationChannel;
import tn.iteam.notification.NotificationChannelType;
import tn.iteam.notification.NotificationMessage;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.notifications.mail", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AtomicBoolean authenticationFailureLogged = new AtomicBoolean(false);

    @Value("${app.notifications.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.notifications.mail.from:no-reply@monitorflow.local}")
    private String mailFrom;

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    @Retry(name = "notificationMail", fallbackMethod = "recoverEmailFailure")
    public void send(NotificationMessage message) {
        if (!mailEnabled || message == null || message.recipientEmail() == null || message.recipientEmail().isBlank()) {
            return;
        }

        try {
            Context context = new Context(Locale.ENGLISH);
            Map<String, Object> attrs = message.attributes();
            if (attrs != null) {
                attrs.forEach(context::setVariable);
            }

            context.setVariable("title", message.title());
            context.setVariable("message", message.message());
            context.setVariable("severity", message.severity() != null ? message.severity().name() : "INFO");
            context.setVariable("actionUrl", message.actionUrl());
            context.setVariable("eventType", message.eventType());

            String template = message.templateName() != null && !message.templateName().isBlank()
                    ? message.templateName()
                    : "email/notification";
            String subject = message.emailSubject() != null && !message.emailSubject().isBlank()
                    ? message.emailSubject()
                    : "[MonitorFlow] " + message.title();

            String html = templateEngine.process(template, context);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(message.recipientEmail().trim());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (MailAuthenticationException exception) {
            if (authenticationFailureLogged.compareAndSet(false, true)) {
                log.warn("SMTP authentication failed, email notifications continue in degraded mode until credentials are fixed");
            }
            log.warn("Email notification skipped for eventId={} due to SMTP authentication failure", message.eventId());
            throw exception;
        } catch (MailException exception) {
            log.warn("Email notification transport failed for eventId={}: {}", message.eventId(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            if (isAuthenticationFailure(exception)) {
                if (authenticationFailureLogged.compareAndSet(false, true)) {
                    log.warn("SMTP authentication failed, email notifications continue in degraded mode until credentials are fixed");
                }
                log.warn("Email notification skipped for eventId={} due to nested SMTP authentication failure", message.eventId());
                MailAuthenticationException authException = new MailAuthenticationException("SMTP authentication failed");
                authException.initCause(exception);
                throw authException;
            }
            log.warn("Email notification failed for eventId={}: {}", message.eventId(), exception.getMessage());
            throw new IllegalStateException("Email notification failed", exception);
        }
    }

    public void recoverEmailFailure(NotificationMessage message, Throwable exception) {
        String eventId = message != null ? message.eventId() : "unknown";
        log.error("Email notification abandoned after retries for eventId={}: {}", eventId, exception.getMessage());
    }

    private boolean isAuthenticationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MailAuthenticationException || current instanceof AuthenticationFailedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
