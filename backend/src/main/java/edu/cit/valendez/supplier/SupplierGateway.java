package edu.cit.valendez.supplier;

import java.util.List;

public interface SupplierGateway {

    /**
     * Initiates a product reorder with LegacySupply.
     * Maps product to supplier catalog terms, persists order as PENDING,
     * and attempts immediate delivery to LegacySupply.
     */
    SupplierOrderDto reorderProduct(String productId, int targetQuantity);

    /**
     * Retrieves all supplier purchase orders.
     */
    List<SupplierOrderDto> getAllSupplierOrders();

    /**
     * Triggers background synchronization of open supplier orders with LegacySupply.
     */
    void syncOrderStatus();

    /**
     * Reports whether the LegacySupply health endpoint is currently reachable.
     */
    boolean isSupplierAvailable();

    /**
     * Checks if there is an active/open supplier purchase order pending delivery for a product.
     */
    boolean hasOpenSupplierOrder(String productId);
}

