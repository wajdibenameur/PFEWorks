package tn.iteam.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationDispatchRecordRepository extends JpaRepository<NotificationDispatchRecord, Long> {
    boolean existsByEventIdAndRecipientIdAndChannel(String eventId, Long recipientId, NotificationChannelType channel);
}

