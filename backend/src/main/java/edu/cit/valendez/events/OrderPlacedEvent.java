package edu.cit.valendez.events;

import java.time.OffsetDateTime;
import java.util.List;

public class OrderPlacedEvent {
    private final Long orderId;
    private final List<OrderItemDto> items;
    private final OffsetDateTime timestamp;

    public OrderPlacedEvent(Long orderId, List<OrderItemDto> items) {
        this.orderId = orderId;
        this.items = items;
        this.timestamp = OffsetDateTime.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}

