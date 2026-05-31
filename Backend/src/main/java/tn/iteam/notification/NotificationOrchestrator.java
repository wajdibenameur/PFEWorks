package tn.iteam.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private final NotificationPolicyEngine policyEngine;
    private final NotificationDispatcher dispatcher;

    public void dispatch(NotificationMessage message) {
        DispatchPlan plan = policyEngine.resolvePlan(message);
        dispatcher.execute(plan);
    }
}

