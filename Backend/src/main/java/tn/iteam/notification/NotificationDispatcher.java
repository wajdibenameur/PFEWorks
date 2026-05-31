package tn.iteam.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;
    private final NotificationDispatchRecordRepository dispatchRecordRepository;

    @Transactional
    public void execute(DispatchPlan plan) {
        if (plan == null || plan.message() == null) {
            return;
        }
        NotificationMessage message = plan.message();

        var targetChannels = plan.channels();
        if (targetChannels == null || targetChannels.isEmpty()) {
            log.info("notification_dispatch eventId={} recipientId={} status=SKIPPED reason=NO_CHANNEL_POLICY",
                    message.eventId(), message.recipientId());
            return;
        }

        Map<NotificationChannelType, NotificationChannel> byType = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::type, Function.identity(), (left, right) -> left));

        for (NotificationChannelType channelType : targetChannels) {
            NotificationChannel channel = byType.get(channelType);
            if (channel == null) {
                log.warn("notification_dispatch eventId={} recipientId={} channel={} status=FAILED reason=CHANNEL_NOT_REGISTERED",
                        message.eventId(), message.recipientId(), channelType);
                continue;
            }

            if (isDuplicate(message, channelType)) {
                log.info("notification_dispatch eventId={} recipientId={} channel={} status=SKIPPED_DUPLICATE",
                        message.eventId(), message.recipientId(), channelType);
                continue;
            }

            try {
                channel.send(message);
                saveDispatchRecord(message, channelType, NotificationDispatchStatus.SUCCESS, null);
                log.info("notification_dispatch eventId={} recipientId={} channel={} status=SUCCESS",
                        message.eventId(), message.recipientId(), channelType);
            } catch (Exception exception) {
                saveDispatchRecord(message, channelType, NotificationDispatchStatus.FAILED, exception.getMessage());
                log.warn("notification_dispatch eventId={} recipientId={} channel={} status=FAILED error={}",
                        message.eventId(), message.recipientId(), channelType, exception.getMessage());
            }
        }
    }

    private boolean isDuplicate(NotificationMessage message, NotificationChannelType channelType) {
        if (message.eventId() == null || message.eventId().isBlank() || message.recipientId() == null) {
            return false;
        }
        return dispatchRecordRepository.existsByEventIdAndRecipientIdAndChannel(
                message.eventId(),
                message.recipientId(),
                channelType
        );
    }

    private void saveDispatchRecord(
            NotificationMessage message,
            NotificationChannelType channelType,
            NotificationDispatchStatus status,
            String lastError
    ) {
        if (message.eventId() == null || message.eventId().isBlank() || message.recipientId() == null) {
            return;
        }
        try {
            NotificationDispatchRecord record = new NotificationDispatchRecord();
            record.setEventId(message.eventId());
            record.setRecipientId(message.recipientId());
            record.setChannel(channelType);
            record.setStatus(status);
            record.setLastError(lastError != null && lastError.length() > 600 ? lastError.substring(0, 600) : lastError);
            dispatchRecordRepository.save(record);
        } catch (DataIntegrityViolationException ignored) {
            // Another concurrent request already recorded the same delivery key.
        }
    }
}
