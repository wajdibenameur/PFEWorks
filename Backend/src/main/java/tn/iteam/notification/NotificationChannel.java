package tn.iteam.notification;

public interface NotificationChannel {
    NotificationChannelType type();

    void send(NotificationMessage message);
}

