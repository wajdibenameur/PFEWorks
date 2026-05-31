package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRoomArchiveScheduler {

    private final ChatRoomService roomService;

    @Value("${app.chat.auto-archive.enabled:false}")
    private boolean enabled;

    @Value("${app.chat.auto-archive.closed-days:30}")
    private long closedDays;

    @Scheduled(cron = "${app.chat.auto-archive.cron:0 30 2 * * *}")
    public void archiveLockedRooms() {
        if (!enabled || closedDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(closedDays, ChronoUnit.DAYS);
        int archivedCount = roomService.archiveClosedRoomsBefore(cutoff);
        if (archivedCount > 0) {
            log.info("Archived {} closed chat rooms older than {} days", archivedCount, closedDays);
        }
    }
}
