package edu.cit.valendez.shop;

import edu.cit.valendez.inventory.InventoryItemDto;

public class OrderResponse {

    private Long orderId;
    private String status;
    private String reason;
    private InventoryItemDto inventory;

    public OrderResponse() {
    }

    public OrderResponse(Long orderId, String status, String reason, InventoryItemDto inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public InventoryItemDto getInventory() {
        return inventory;
    }

    public void setInventory(InventoryItemDto inventory) {
        this.inventory = inventory;
    }
}

