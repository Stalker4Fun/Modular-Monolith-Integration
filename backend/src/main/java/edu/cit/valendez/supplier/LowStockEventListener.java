package edu.cit.valendez.supplier;

import edu.cit.valendez.events.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener in the Supplier ACL module.
 * Listens for LowStockEvent and triggers a replenishment purchase order through SupplierGateway.
 * Depends ONLY on domain events from edu.cit.valendez.events.
 */
@Component
class LowStockEventListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockEventListener.class);
    private final SupplierGateway supplierGateway;

    LowStockEventListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        log.info("[Supplier ACL] Low-stock event received for product {} (stock: {}, threshold: {}). Initiating auto-reorder...",
                event.getProductId(), event.getRemainingStock(), event.getThreshold());

        try {
            SupplierOrderDto orderDto = supplierGateway.reorderProduct(event.getProductId(), 10);
            log.info("[Supplier ACL] Replenishment order initiated successfully. Supplier Order ID: {}, Status: {}, BuyerRef: {}",
                    orderDto.getId(), orderDto.getStatus(), orderDto.getBuyerRef());
        } catch (Exception e) {
            log.error("[Supplier ACL] Failed to initiate auto-reorder for product {}: {}", event.getProductId(), e.getMessage(), e);
        }
    }
}

