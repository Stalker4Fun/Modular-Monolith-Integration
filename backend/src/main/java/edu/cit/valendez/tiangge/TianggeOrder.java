package edu.cit.valendez.tiangge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

/**
 * JPA entity representing a Tiangge marketplace order.
 * Enforces UNIQUE constraint on tiangge_order_id for idempotency and deduplication.
 * Package-private to enforce module isolation.
 */
@Entity
@Table(
        name = "tiangge_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tiangge_order_id", columnNames = {"tiangge_order_id"})
        },
        indexes = {
                @Index(name = "idx_tiangge_orders_status", columnList = "status"),
                @Index(name = "idx_tiangge_orders_product_id", columnList = "product_id")
        }
)
class TianggeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, unique = true, length = 100)
    private String tianggeOrderId;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TianggeOrderStatus status;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public TianggeOrder() {
    }

    public TianggeOrder(String tianggeOrderId, String productId, int quantity, TianggeOrderStatus status) {
        this.tianggeOrderId = tianggeOrderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTianggeOrderId() {
        return tianggeOrderId;
    }

    public void setTianggeOrderId(String tianggeOrderId) {
        this.tianggeOrderId = tianggeOrderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public TianggeOrderStatus getStatus() {
        return status;
    }

    public void setStatus(TianggeOrderStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
