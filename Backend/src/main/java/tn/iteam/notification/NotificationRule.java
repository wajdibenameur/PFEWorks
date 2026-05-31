package tn.iteam.notification;

public interface NotificationRule {
    int priority();

    boolean matches(NotificationMessage message);

    DispatchPlan buildPlan(NotificationMessage message);
}

