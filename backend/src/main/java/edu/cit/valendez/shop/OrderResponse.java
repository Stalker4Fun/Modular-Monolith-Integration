package edu.cit.valendez.shop;

import edu.cit.valendez.events.OrderItemDto;
import edu.cit.valendez.inventory.InventoryItemDto;

import java.util.List;

public class OrderResponse {

    private Long orderId;
    private String status;
    private String reason;
    private List<OrderItemDto> items;
    private List<InventoryItemDto> inventory;

    public OrderResponse() {
    }

    public OrderResponse(Long orderId, String status, String reason, List<OrderItemDto> items,
                         List<InventoryItemDto> inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items;
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

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }

    public List<InventoryItemDto> getInventory() {
        return inventory;
    }

    public void setInventory(List<InventoryItemDto> inventory) {
        this.inventory = inventory;
    }
}
