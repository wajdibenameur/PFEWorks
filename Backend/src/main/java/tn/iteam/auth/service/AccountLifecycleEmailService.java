package tn.iteam.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.iteam.notification.NotificationFactory;
import tn.iteam.notification.NotificationOrchestrator;

@Service
@RequiredArgsConstructor
public class AccountLifecycleEmailService {

    private final NotificationFactory notificationFactory;
    private final NotificationOrchestrator notificationOrchestrator;

    @Value("${app.auth.frontend-callback-url:http://localhost:4200/auth/callback}")
    private String frontendCallbackUrl;

    public void sendAccountCreatedAndActivated(String toEmail, String username, String roleName) {
        dispatchLifecycleMail(toEmail, username, roleName, "ACCOUNT_CREATED_ACTIVATED",
                "Your account has been created and activated. You can now sign in.",
                "Your MonitorFlow account is active");
    }

    public void sendAccountDeactivated(String toEmail, String username) {
        dispatchLifecycleMail(toEmail, username, null, "ACCOUNT_DEACTIVATED",
                "Your account has been deactivated. Please contact your administrator if needed.",
                "Your MonitorFlow account has been deactivated");
    }

    public void sendAccountReactivated(String toEmail, String username) {
        dispatchLifecycleMail(toEmail, username, null, "ACCOUNT_REACTIVATED",
                "Your account has been reactivated. You can sign in again.",
                "Your MonitorFlow account has been reactivated");
    }

    private void dispatchLifecycleMail(
            String toEmail,
            String username,
            String roleName,
            String state,
            String summary,
            String subject
    ) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        notificationOrchestrator.dispatch(
                notificationFactory.createAccountLifecycleNotification(
                        toEmail,
                        username,
                        roleName,
                        state,
                        summary,
                        subject,
                        resolveLoginUrl()
                )
        );
    }

    private String resolveLoginUrl() {
        if (frontendCallbackUrl == null || frontendCallbackUrl.isBlank()) {
            return "http://localhost:4200/login";
        }
        String base = frontendCallbackUrl.trim();
        int idx = base.indexOf("/auth/callback");
        if (idx > 0) {
            return base.substring(0, idx) + "/login";
        }
        return base.endsWith("/login") ? base : base + "/login";
    }
}
