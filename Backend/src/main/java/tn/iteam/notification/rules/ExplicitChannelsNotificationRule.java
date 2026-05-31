package tn.iteam.notification.rules;

import org.springframework.stereotype.Component;
import tn.iteam.notification.DeliveryContract;
import tn.iteam.notification.DispatchPlan;
import tn.iteam.notification.NotificationMessage;
import tn.iteam.notification.NotificationRule;

@Component
public class ExplicitChannelsNotificationRule implements NotificationRule {
    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean matches(NotificationMessage message) {
        return message != null && message.channels() != null && !message.channels().isEmpty();
    }

    @Override
    public DispatchPlan buildPlan(NotificationMessage message) {
        return DispatchPlan.builder()
                .message(message)
                .channels(message.channels())
                .contract(DeliveryContract.bestEffortDefault())
                .build();
    }
}

