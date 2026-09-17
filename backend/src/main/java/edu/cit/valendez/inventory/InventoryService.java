package edu.cit.valendez.inventory;

import java.util.List;

public interface InventoryService {

    /**
     * Retrieves the current inventory item details by product ID.
     *
     * @param productId product identifier
     * @return InventoryItemDto or null if not found
     */
    InventoryItemDto getItem(String productId);

    /**
     * Attempts to reserve the requested quantity of a product.
     * Reads/updates the inventory table; rejects if requested quantity exceeds stock.
     *
     * @param productId product identifier
     * @param quantity  requested quantity
     * @return ReservationResult containing confirmation or rejection status, reason, and inventory details
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Restocks the specified quantity of a product, e.g. when an order is cancelled.
     * Reads/updates the inventory table.
     *
     * @param productId product identifier
     * @param quantity  quantity to return to stock
     * @return ReservationResult containing updated inventory details
     */
    ReservationResult restock(String productId, int quantity);

    /**
     * Retrieves all inventory items, useful for populating dropdown selections and live tables.
     *
     * @return list of inventory items
     */
    List<InventoryItemDto> getAllItems();
}
