package edu.cit.valendez.shop;

import java.util.ArrayList;
import java.util.List;

public class OrderRequest {

    // Multi-item order support (Lab 2)
    private List<OrderItemRequest> items;

    // Single-item backward compatibility (Lab 1)
    private String productId;
    private int quantity;

    public OrderRequest() {
    }

    public OrderRequest(List<OrderItemRequest> items) {
        this.items = items;
    }

    public OrderRequest(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.items = List.of(new OrderItemRequest(productId, quantity));
    }

    public List<OrderItemRequest> getItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productId != null && !productId.trim().isEmpty() && quantity > 0) {
            List<OrderItemRequest> single = new ArrayList<>();
            single.add(new OrderItemRequest(productId.trim(), quantity));
            return single;
        }
        return items != null ? items : new ArrayList<>();
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
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
}
