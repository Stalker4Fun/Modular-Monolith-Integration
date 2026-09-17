package edu.cit.valendez.events;

import java.time.OffsetDateTime;

public class LowStockEvent {
    private final String productId;
    private final String productName;
    private final int remainingStock;
    private final int threshold;
    private final OffsetDateTime timestamp;

    public LowStockEvent(String productId, String productName, int remainingStock, int threshold) {
        this.productId = productId;
        this.productName = productName;
        this.remainingStock = remainingStock;
        this.threshold = threshold;
        this.timestamp = OffsetDateTime.now();
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public int getThreshold() {
        return threshold;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}

