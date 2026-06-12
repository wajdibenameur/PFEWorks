package tn.iteam.service.support;

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
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.User;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.notifications.mail", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AtomicBoolean authenticationFailureLogged = new AtomicBoolean(false);

    @Value("${app.notifications.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.notifications.mail.from:no-reply@monitorflow.local}")
    private String mailFrom;

    @Retry(name = "notificationMail", fallbackMethod = "recoverMailFailure")
    public void sendNotificationMail(User recipient, NotificationEntity notification) {
        if (!mailEnabled || recipient == null || notification == null) {
            return;
        }
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }

        try {
            Context context = new Context(Locale.ENGLISH);
            context.setVariable("title", notification.getTitle());
            context.setVariable("message", notification.getMessage());
            context.setVariable("severity", notification.getSeverity().name());
            context.setVariable("actionUrl", notification.getActionUrl());
            context.setVariable("eventType", notification.getEventType());

            String html = templateEngine.process("email/notification", context);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(recipient.getEmail().trim());
            helper.setSubject("[MonitorFlow] " + notification.getTitle());
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (MailAuthenticationException exception) {
            if (authenticationFailureLogged.compareAndSet(false, true)) {
                log.warn("SMTP authentication failed, notification emails continue in degraded mode until credentials are fixed");
            }
            log.warn("Mail notification skipped for user {} due to SMTP authentication failure", recipient.getUsername());
            throw exception;
        } catch (MailException exception) {
            log.warn("Mail notification transport failed for user {}: {}", recipient.getUsername(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            if (isAuthenticationFailure(exception)) {
                if (authenticationFailureLogged.compareAndSet(false, true)) {
                    log.warn("SMTP authentication failed, notification emails continue in degraded mode until credentials are fixed");
                }
                log.warn("Mail notification skipped for user {} due to nested SMTP authentication failure", recipient.getUsername());
                MailAuthenticationException authException = new MailAuthenticationException("SMTP authentication failed");
                authException.initCause(exception);
                throw authException;
            }
            log.warn("Mail notification failed for user {}: {}", recipient.getUsername(), exception.getMessage());
            throw new IllegalStateException("Mail send failed", exception);
        }
    }

    public void recoverMailFailure(User recipient, NotificationEntity notification, Throwable exception) {
        String username = recipient != null ? recipient.getUsername() : "unknown";
        log.error("Mail notification abandoned after retries for user {}: {}", username, exception.getMessage());
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
