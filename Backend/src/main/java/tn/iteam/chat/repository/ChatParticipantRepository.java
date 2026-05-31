package tn.iteam.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.iteam.chat.domain.ChatParticipant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    Optional<ChatParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("""
            select p
            from ChatParticipant p
            join fetch p.room r
            where p.userId = :userId
              and p.active = true
              and r.archived = false
            order by p.updatedAt desc
            """)
    List<ChatParticipant> findActiveByUserIdWithRoomOrderByUpdatedAtDesc(@Param("userId") Long userId);

    List<ChatParticipant> findByRoomIdAndActiveTrue(Long roomId);
}
