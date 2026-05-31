package tn.iteam.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.domain.ChatRoomStatus;
import tn.iteam.chat.domain.ChatRoomType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByTicketId(Long ticketId);

    List<ChatRoom> findByRoomType(ChatRoomType roomType);

    Optional<ChatRoom> findByPrivateChatKey(String privateChatKey);

    @Query("""
            select r from ChatRoom r
            where r.roomType = 'PRIVATE'
              and exists (select 1 from ChatParticipant p1 where p1.room = r and p1.userId = :userA and p1.active = true)
              and exists (select 1 from ChatParticipant p2 where p2.room = r and p2.userId = :userB and p2.active = true)
            """)
    List<ChatRoom> findPrivateRoomsByUserPair(Long userA, Long userB);

    List<ChatRoom> findByStatusAndArchivedFalseAndClosedAtBefore(ChatRoomStatus status, Instant before);

    @Query("""
            select distinct r from ChatRoom r
            join ChatParticipant p on p.room = r
            where p.userId = :userId
              and p.active = true
              and r.archived = :archived
            order by r.updatedAt desc
            """)
    List<ChatRoom> findRoomsByUserIdAndArchived(Long userId, boolean archived);
}
