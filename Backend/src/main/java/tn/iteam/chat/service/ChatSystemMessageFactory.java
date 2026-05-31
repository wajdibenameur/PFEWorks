package tn.iteam.chat.service;

import org.springframework.stereotype.Component;

@Component
public class ChatSystemMessageFactory {

    public String roomCreated(SystemMessageContext context) {
        return "Incident room created by " + context.actorUsername() + " for ticket #" + context.ticketId();
    }

    public String participantAdded(String username, SystemMessageContext context) {
        return context.actorUsername() + " added " + username + " to the room";
    }

    public String roomClosed(SystemMessageContext context) {
        return "Room closed by " + context.actorUsername();
    }

    public String ticketLinked(SystemMessageContext context) {
        return "Room linked to ticket #" + context.ticketId();
    }
}
