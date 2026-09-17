package edu.cit.valendez.events;

import java.time.OffsetDateTime;
import java.util.List;

public class OrderRejectedEvent {
    private final Long orderId;
    private final String reason;
    private final List<OrderItemDto> items;
    private final OffsetDateTime timestamp;

    public OrderRejectedEvent(Long orderId, String reason, List<OrderItemDto> items) {
        this.orderId = orderId;
        this.reason = reason;
        this.items = items;
        this.timestamp = OffsetDateTime.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}

