package tn.iteam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.NotificationEntity;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    Page<NotificationEntity> findByRecipientIdAndArchivedFalse(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalseAndArchivedFalse(Long recipientId);

    Optional<NotificationEntity> findByIdAndRecipientId(Long id, Long recipientId);

    Optional<NotificationEntity> findByRecipientIdAndEventId(Long recipientId, String eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationEntity n
            set n.read = true
            where n.recipient.id = :recipientId
              and n.archived = false
              and n.read = false
            """)
    int markAllRead(@Param("recipientId") Long recipientId);
}
