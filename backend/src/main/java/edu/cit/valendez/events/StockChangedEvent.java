package edu.cit.valendez.events;

import java.time.OffsetDateTime;

public class StockChangedEvent {

    private final String productId;
    private final int newStock;
    private final OffsetDateTime timestamp;

    public StockChangedEvent(String productId, int newStock) {
        this.productId = productId;
        this.newStock = newStock;
        this.timestamp = OffsetDateTime.now();
    }

    public String getProductId() {
        return productId;
    }

    public int getNewStock() {
        return newStock;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}
