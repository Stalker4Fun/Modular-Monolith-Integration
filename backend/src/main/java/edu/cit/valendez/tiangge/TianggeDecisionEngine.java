package edu.cit.valendez.tiangge;

import edu.cit.valendez.inventory.InventoryItemDto;
import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.inventory.ReservationResult;
import edu.cit.valendez.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Decision Engine evaluating Tiangge marketplace orders within 60s window.
 * Enforces Stock > 0 -> ACCEPTED, Stock == 0 & PO coming -> BACKORDERED, Stock == 0 & no PO -> REJECTED.
 * Package-private to enforce module isolation.
 */
@Service
class TianggeDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(TianggeDecisionEngine.class);

    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeOrderRepository tianggeOrderRepository;
    private final TianggeClient tianggeClient;

    public TianggeDecisionEngine(
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            TianggeOrderRepository tianggeOrderRepository,
            TianggeClient tianggeClient
    ) {
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.tianggeOrderRepository = tianggeOrderRepository;
        this.tianggeClient = tianggeClient;
    }

    /**
     * Evaluates a newly ingested Tiangge order and executes decision workflow.
     */
    @Transactional
    public void evaluateAndProcessOrder(TianggeOrder order) {
        String productId = order.getProductId();
        int requestedQty = order.getQuantity();

        InventoryItemDto item = inventoryService.getItem(productId);
        int currentStock = (item != null) ? item.getStock() : 0;

        if (currentStock >= requestedQty && currentStock > 0) {
            ReservationResult result = inventoryService.reserve(productId, requestedQty);
            if (result.isSuccess()) {
                order.setStatus(TianggeOrderStatus.ACCEPTED);
                order.setReason("Stock reserved successfully");
                tianggeOrderRepository.save(order);
                log.info("[Tiangge Decision] Order {} ACCEPTED (reserved {} units of {})",
                        order.getTianggeOrderId(), requestedQty, productId);

                tianggeClient.sendDecision(order.getTianggeOrderId(), "ACCEPTED", order.getReason());
                return;
            }
        }

        // Stock is insufficient or reservation failed
        boolean openPoExists = supplierGateway.hasOpenSupplierOrder(productId);
        if (!openPoExists && currentStock == 0) {
            try {
                log.info("[Tiangge Decision] No open PO for product {}. Initiating supplier replenishment.", productId);
                supplierGateway.reorderProduct(productId, 10);
                openPoExists = supplierGateway.hasOpenSupplierOrder(productId);
            } catch (Exception e) {
                log.warn("[Tiangge Decision] Auto-reorder failed for product {}: {}", productId, e.getMessage());
            }
        }

        if (openPoExists) {
            order.setStatus(TianggeOrderStatus.BACKORDERED);
            order.setReason("Out of stock; awaiting supplier replenishment order");
            tianggeOrderRepository.save(order);
            log.info("[Tiangge Decision] Order {} BACKORDERED for product {} (open PO exists)",
                    order.getTianggeOrderId(), productId);

            tianggeClient.sendDecision(order.getTianggeOrderId(), "BACKORDERED", order.getReason());
        } else {
            order.setStatus(TianggeOrderStatus.REJECTED);
            order.setReason(String.format("Out of stock (%d available) and no open supplier PO", currentStock));
            tianggeOrderRepository.save(order);
            log.info("[Tiangge Decision] Order {} REJECTED for product {}",
                    order.getTianggeOrderId(), productId);

            tianggeClient.sendDecision(order.getTianggeOrderId(), "REJECTED", order.getReason());
        }
    }

    /**
     * Processes marketplace cancellation feed events, triggers inventory restock, and confirms to Tiangge API.
     */
    @Transactional
    public void processCancellation(String tianggeOrderId) {
        Optional<TianggeOrder> optionalOrder = tianggeOrderRepository.findByTianggeOrderId(tianggeOrderId);
        if (optionalOrder.isEmpty()) {
            log.warn("[Tiangge Cancellation] Order {} not found for cancellation", tianggeOrderId);
            tianggeClient.sendCancellationConfirmation(tianggeOrderId, "Order not found in database");
            return;
        }

        TianggeOrder order = optionalOrder.get();
        if (order.getStatus() == TianggeOrderStatus.CANCELLED) {
            log.info("[Tiangge Cancellation] Order {} is already CANCELLED", tianggeOrderId);
            return;
        }

        if (order.getStatus() == TianggeOrderStatus.ACCEPTED) {
            log.info("[Tiangge Cancellation] Restocking {} units of {} for cancelled order {}",
                    order.getQuantity(), order.getProductId(), tianggeOrderId);
            inventoryService.restock(order.getProductId(), order.getQuantity());
        }

        order.setStatus(TianggeOrderStatus.CANCELLED);
        order.setReason("Cancelled via Tiangge feed event");
        tianggeOrderRepository.save(order);

        tianggeClient.sendCancellationConfirmation(tianggeOrderId, order.getReason());

        // Push updated stock to Tiangge API
        InventoryItemDto item = inventoryService.getItem(order.getProductId());
        if (item != null) {
            tianggeClient.sendStockUpdate(order.getProductId(), item.getStock());
        }
    }

    /**
     * Attempts to fulfill pending BACKORDERED orders when new stock is delivered.
     */
    @Transactional
    public void fulfillBackordersForProduct(String productId) {
        List<TianggeOrder> backorders = tianggeOrderRepository.findByProductIdAndStatus(
                productId, TianggeOrderStatus.BACKORDERED
        );

        if (backorders.isEmpty()) {
            return;
        }

        log.info("[Tiangge Backorders] Evaluating {} backordered orders for product {}", backorders.size(), productId);

        for (TianggeOrder order : backorders) {
            InventoryItemDto item = inventoryService.getItem(productId);
            if (item == null || item.getStock() < order.getQuantity()) {
                log.info("[Tiangge Backorders] Stock insufficient for order {} (requires {}, has {})",
                        order.getTianggeOrderId(), order.getQuantity(), item != null ? item.getStock() : 0);
                continue;
            }

            ReservationResult result = inventoryService.reserve(productId, order.getQuantity());
            if (result.isSuccess()) {
                order.setStatus(TianggeOrderStatus.ACCEPTED);
                order.setReason("Fulfilled from supplier delivery restock");
                tianggeOrderRepository.save(order);

                log.info("[Tiangge Backorders] Backordered order {} successfully ACCEPTED!", order.getTianggeOrderId());
                tianggeClient.resolveBackorder(order.getTianggeOrderId(), "ACCEPTED");
                tianggeClient.sendDecision(order.getTianggeOrderId(), "ACCEPTED", order.getReason());
                if (result.getInventory() != null) {
                    tianggeClient.sendStockUpdate(productId, result.getInventory().getStock());
                }
            }
        }
    }
}
