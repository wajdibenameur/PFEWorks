package tn.iteam.chat.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSystemMessageFactoryTest {

    private final ChatSystemMessageFactory factory = new ChatSystemMessageFactory();

    @Test
    void shouldGenerateRoomCreatedMessageWithContext() {
        SystemMessageContext context = SystemMessageContext.builder()
                .actorId(1L)
                .actorUsername("superadmin")
                .ticketId(444L)
                .reason("ROOM_CREATED")
                .metadata(Map.of("severity", "CRITICAL"))
                .build();

        String message = factory.roomCreated(context);
        assertTrue(message.contains("superadmin"));
        assertTrue(message.contains("#444"));
    }
}

