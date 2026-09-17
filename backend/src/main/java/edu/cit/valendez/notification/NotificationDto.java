package edu.cit.valendez.notification;

import java.time.OffsetDateTime;

public class NotificationDto {
    private Long notificationId;
    private String message;
    private OffsetDateTime createdAt;

    public NotificationDto() {
    }

    public NotificationDto(Long notificationId, String message, OffsetDateTime createdAt) {
        this.notificationId = notificationId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

