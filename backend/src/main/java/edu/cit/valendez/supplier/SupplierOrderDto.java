package edu.cit.valendez.supplier;

import java.time.OffsetDateTime;

public class SupplierOrderDto {
    private final Long id;
    private final String productId;
    private final String buyerRef;
    private final String requestId;
    private final String poNumber;
    private final String supplierSku;
    private final int cases;
    private final int units;
    private final String status;
    private final String failureReason;
    private final int retryCount;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public SupplierOrderDto(Long id, String productId, String buyerRef, String requestId, String poNumber,
                            String supplierSku, int cases, int units, String status, String failureReason,
                            int retryCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.poNumber = poNumber;
        this.supplierSku = supplierSku;
        this.cases = cases;
        this.units = units;
        this.status = status;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getProductId() { return productId; }
    public String getBuyerRef() { return buyerRef; }
    public String getRequestId() { return requestId; }
    public String getPoNumber() { return poNumber; }
    public String getSupplierSku() { return supplierSku; }
    public int getCases() { return cases; }
    public int getUnits() { return units; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public int getRetryCount() { return retryCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

