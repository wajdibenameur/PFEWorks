package tn.iteam.notification;

import lombok.Builder;

import java.util.Set;

@Builder
public record DispatchPlan(
        NotificationMessage message,
        Set<NotificationChannelType> channels,
        DeliveryContract contract
) {
}

