package tn.iteam.notification;

public interface NotificationPolicyEngine {
    DispatchPlan resolvePlan(NotificationMessage message);
}
