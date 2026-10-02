package edu.cit.valendez.tiangge;

import edu.cit.valendez.events.DeliveryReceivedEvent;
import edu.cit.valendez.events.StockChangedEvent;
import edu.cit.valendez.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring EventListener component decoupling Tiangge module from Order and Inventory modules.
 * Listens for StockChangedEvent and DeliveryReceivedEvent to synchronize stock and fulfill backorders.
 * Package-private to enforce module isolation.
 */
@Component
class TianggeEventListener {

    private static final Logger log = LoggerFactory.getLogger(TianggeEventListener.class);

    private final TianggeClient tianggeClient;
    private final TianggeDecisionEngine decisionEngine;

    public TianggeEventListener(TianggeClient tianggeClient, TianggeDecisionEngine decisionEngine) {
        this.tianggeClient = tianggeClient;
        this.decisionEngine = decisionEngine;
    }

    /**
     * Synchronizes updated inventory stock level with Tiangge API.
     */
    @EventListener
    public void onStockChanged(StockChangedEvent event) {
        log.info("[Tiangge EventListener] StockChangedEvent received for product {}: new stock = {}",
                event.getProductId(), event.getNewStock());
        tianggeClient.sendStockUpdate(event.getProductId(), event.getNewStock());
    }

    /**
     * Resolves pending BACKORDERED items when a supplier delivery is received.
     */
    @EventListener
    @Transactional
    public void onDeliveryReceived(DeliveryReceivedEvent event) {
        log.info("[Tiangge EventListener] DeliveryReceivedEvent received for product {}: +{} units (PO: {})",
                event.getProductId(), event.getQuantity(), event.getPoNumber());

        decisionEngine.fulfillBackordersForProduct(event.getProductId());
    }

    /**
     * Fallback listener for SupplierOrderDeliveredEvent to ensure backorder resolution.
     */
    @EventListener
    @Transactional
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        log.info("[Tiangge EventListener] SupplierOrderDeliveredEvent received for product {}: +{} units (PO: {})",
                event.getProductId(), event.getQuantity(), event.getPoNumber());

        decisionEngine.fulfillBackordersForProduct(event.getProductId());
    }
}
