package tn.iteam.notification.rules;

import org.springframework.stereotype.Component;
import tn.iteam.notification.DeliveryContract;
import tn.iteam.notification.DispatchPlan;
import tn.iteam.notification.NotificationChannelType;
import tn.iteam.notification.NotificationMessage;
import tn.iteam.notification.NotificationRule;

import java.util.Set;

@Component
public class InfoNotificationRule implements NotificationRule {
    @Override
    public int priority() {
        return 99;
    }

    @Override
    public boolean matches(NotificationMessage message) {
        return message != null;
    }

    @Override
    public DispatchPlan buildPlan(NotificationMessage message) {
        return DispatchPlan.builder()
                .message(message)
                .channels(Set.of(NotificationChannelType.WEBSOCKET))
                .contract(DeliveryContract.bestEffortDefault())
                .build();
    }
}

