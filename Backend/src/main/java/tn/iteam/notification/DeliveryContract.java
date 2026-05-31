package tn.iteam.notification;

import lombok.Builder;

import java.util.Set;

@Builder
public record DeliveryContract(
        int maxAttempts,
        long backoffMs,
        long timeoutMs,
        DeliveryGuarantee guarantee,
        Set<NotificationChannelType> fallbackChannels
) {
    public static DeliveryContract bestEffortDefault() {
        return DeliveryContract.builder()
                .maxAttempts(1)
                .backoffMs(0)
                .timeoutMs(3000)
                .guarantee(DeliveryGuarantee.BEST_EFFORT)
                .fallbackChannels(Set.of())
                .build();
    }
}

