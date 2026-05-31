package tn.iteam.notification;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class DefaultNotificationPolicyEngine implements NotificationPolicyEngine {

    private final List<NotificationRule> orderedRules;

    public DefaultNotificationPolicyEngine(List<NotificationRule> rules) {
        this.orderedRules = rules.stream()
                .sorted(Comparator.comparingInt(NotificationRule::priority))
                .toList();
    }

    @Override
    public DispatchPlan resolvePlan(NotificationMessage message) {
        if (message == null) {
            return DispatchPlan.builder()
                    .message(null)
                    .channels(Set.of())
                    .contract(DeliveryContract.bestEffortDefault())
                    .build();
        }

        for (NotificationRule rule : orderedRules) {
            if (rule.matches(message)) {
                return rule.buildPlan(message);
            }
        }

        return DispatchPlan.builder()
                .message(message)
                .channels(Set.of())
                .contract(DeliveryContract.bestEffortDefault())
                .build();
    }
}

