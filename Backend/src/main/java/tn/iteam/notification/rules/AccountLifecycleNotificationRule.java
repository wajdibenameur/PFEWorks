package tn.iteam.notification.rules;

import org.springframework.stereotype.Component;
import tn.iteam.notification.DeliveryContract;
import tn.iteam.notification.DispatchPlan;
import tn.iteam.notification.DeliveryGuarantee;
import tn.iteam.notification.NotificationChannelType;
import tn.iteam.notification.NotificationMessage;
import tn.iteam.notification.NotificationRule;

import java.util.Set;

@Component
public class AccountLifecycleNotificationRule implements NotificationRule {
    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean matches(NotificationMessage message) {
        String eventType = message != null ? message.eventType() : null;
        return eventType != null && eventType.trim().toUpperCase().startsWith("ACCOUNT_");
    }

    @Override
    public DispatchPlan buildPlan(NotificationMessage message) {
        return DispatchPlan.builder()
                .message(message)
                .channels(Set.of(NotificationChannelType.EMAIL))
                .contract(DeliveryContract.builder()
                        .maxAttempts(2)
                        .backoffMs(500)
                        .timeoutMs(5000)
                        .guarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                        .fallbackChannels(Set.of())
                        .build())
                .build();
    }
}

