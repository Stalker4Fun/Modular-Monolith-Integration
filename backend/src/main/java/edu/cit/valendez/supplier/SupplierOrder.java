package edu.cit.valendez.supplier;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.OffsetDateTime;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, length = 100)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "po_number", length = 100)
    private String poNumber;

    @Column(name = "supplier_sku", nullable = false, length = 100)
    private String supplierSku;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SupplierOrderStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /**
     * The earliest time a transiently failed submission may be replayed.  This
     * is persisted so a restart cannot turn a supplier outage into a lost PO.
     */
    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public SupplierOrder() {
    }

    public SupplierOrder(String productId, String buyerRef, String requestId, String supplierSku, int cases, int units) {
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.supplierSku = supplierSku;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getBuyerRef() {
        return buyerRef;
    }

    public void setBuyerRef(String buyerRef) {
        this.buyerRef = buyerRef;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public void setSupplierSku(String supplierSku) {
        this.supplierSku = supplierSku;
    }

    public int getCases() {
        return cases;
    }

    public void setCases(int cases) {
        this.cases = cases;
    }

    public int getUnits() {
        return units;
    }

    public void setUnits(int units) {
        this.units = units;
    }

    public SupplierOrderStatus getStatus() {
        return status;
    }

    public void setStatus(SupplierOrderStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void scheduleRetry(OffsetDateTime now) {
        // 15s, 30s, 60s ... capped at five minutes.  This stays comfortably
        // below the supplier's polling quota during an extended outage.
        int exponent = Math.min(Math.max(retryCount - 1, 0), 5);
        long delaySeconds = Math.min(15L * (1L << exponent), Duration.ofMinutes(5).toSeconds());
        this.nextRetryAt = now.plusSeconds(delaySeconds);
    }

    public boolean isReadyForRetry(OffsetDateTime now) {
        return nextRetryAt == null || !nextRetryAt.isAfter(now);
    }

    public void clearRetrySchedule() {
        this.nextRetryAt = null;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

