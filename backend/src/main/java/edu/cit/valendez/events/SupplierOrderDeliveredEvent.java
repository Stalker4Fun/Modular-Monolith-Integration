package edu.cit.valendez.events;

import java.time.OffsetDateTime;

public class SupplierOrderDeliveredEvent {
    private final String productId;
    private final int quantity;
    private final String poNumber;
    private final OffsetDateTime timestamp;

    public SupplierOrderDeliveredEvent(String productId, int quantity, String poNumber) {
        this.productId = productId;
        this.quantity = quantity;
        this.poNumber = poNumber;
        this.timestamp = OffsetDateTime.now();
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}

