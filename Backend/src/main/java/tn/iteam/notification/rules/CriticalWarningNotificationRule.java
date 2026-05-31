package tn.iteam.notification.rules;

import org.springframework.stereotype.Component;
import tn.iteam.enums.NotificationSeverity;
import tn.iteam.notification.DeliveryContract;
import tn.iteam.notification.DispatchPlan;
import tn.iteam.notification.DeliveryGuarantee;
import tn.iteam.notification.NotificationChannelType;
import tn.iteam.notification.NotificationMessage;
import tn.iteam.notification.NotificationRule;

import java.util.Set;

@Component
public class CriticalWarningNotificationRule implements NotificationRule {
    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean matches(NotificationMessage message) {
        NotificationSeverity severity = message != null ? message.severity() : null;
        return severity == NotificationSeverity.CRITICAL || severity == NotificationSeverity.WARNING;
    }

    @Override
    public DispatchPlan buildPlan(NotificationMessage message) {
        return DispatchPlan.builder()
                .message(message)
                .channels(Set.of(NotificationChannelType.WEBSOCKET, NotificationChannelType.EMAIL))
                .contract(DeliveryContract.builder()
                        .maxAttempts(2)
                        .backoffMs(300)
                        .timeoutMs(3000)
                        .guarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                        .fallbackChannels(Set.of(NotificationChannelType.WEBSOCKET))
                        .build())
                .build();
    }
}

