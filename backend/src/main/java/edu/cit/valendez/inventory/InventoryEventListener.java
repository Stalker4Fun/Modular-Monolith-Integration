package edu.cit.valendez.inventory;

import edu.cit.valendez.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for supplier delivery events and restocks inventory.
 * Strictly decoupled: only imports domain event from edu.cit.valendez.events.
 */
@Component
class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);
    private final InventoryService inventoryService;

    InventoryEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Transactional
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        log.info("[Inventory] Supplier delivery received for product {}: +{} units (PO: {})",
                event.getProductId(), event.getQuantity(), event.getPoNumber());

        ReservationResult result = inventoryService.restock(event.getProductId(), event.getQuantity());
        if (result.isSuccess()) {
            log.info("[Inventory] Restocked product {} successfully. New stock: {}",
                    event.getProductId(), result.getInventory() != null ? result.getInventory().getStock() : "N/A");
        } else {
            log.error("[Inventory] Failed to restock product {}: {}",
                    event.getProductId(), result.getReason());
        }
    }
}
