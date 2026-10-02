package edu.cit.valendez.tiangge;

import edu.cit.valendez.events.DeliveryReceivedEvent;
import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.supplier.SupplierGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TianggeDecisionEngineTest {

    @Autowired
    private TianggeDecisionEngine decisionEngine;

    @Autowired
    private TianggeOrderRepository tianggeOrderRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private SupplierGateway supplierGateway;

    @Autowired
    private TianggeEventListener eventListener;

    @Test
    @DisplayName("Stock > 0 results in ACCEPTED decision and reserves stock")
    void testStockAvailableResultsInAccepted() {
        // P100 has initial stock 25
        TianggeOrder order = new TianggeOrder("T-DEC-001", "P100", 2, TianggeOrderStatus.PENDING);
        tianggeOrderRepository.saveAndFlush(order);

        decisionEngine.evaluateAndProcessOrder(order);

        assertEquals(TianggeOrderStatus.ACCEPTED, order.getStatus());
        assertEquals(23, inventoryService.getItem("P100").getStock());
    }

    @Test
    @DisplayName("Stock == 0 with open PO results in BACKORDERED decision")
    void testStockZeroWithOpenPoResultsInBackordered() {
        // P300 has 0 stock. Trigger reorder so open PO exists
        supplierGateway.reorderProduct("P300", 10);

        TianggeOrder order = new TianggeOrder("T-DEC-002", "P300", 1, TianggeOrderStatus.PENDING);
        tianggeOrderRepository.saveAndFlush(order);

        decisionEngine.evaluateAndProcessOrder(order);

        assertEquals(TianggeOrderStatus.BACKORDERED, order.getStatus());
    }

    @Test
    @DisplayName("Stock == 0 triggers replenishment PO and BACKORDERED decision")
    void testStockZeroTriggersReplenishmentAndBackordered() {
        // P300 has 0 stock and no initial open PO
        TianggeOrder order = new TianggeOrder("T-DEC-003", "P300", 1, TianggeOrderStatus.PENDING);
        tianggeOrderRepository.saveAndFlush(order);

        decisionEngine.evaluateAndProcessOrder(order);

        assertEquals(TianggeOrderStatus.BACKORDERED, order.getStatus());
    }

    @Test
    @DisplayName("Cancellation of ACCEPTED order restocks inventory and sets CANCELLED status")
    void testCancellationRestocksInventory() {
        TianggeOrder order = new TianggeOrder("T-DEC-004", "P100", 5, TianggeOrderStatus.PENDING);
        tianggeOrderRepository.saveAndFlush(order);
        decisionEngine.evaluateAndProcessOrder(order);

        assertEquals(20, inventoryService.getItem("P100").getStock());

        decisionEngine.processCancellation("T-DEC-004");

        Optional<TianggeOrder> updated = tianggeOrderRepository.findByTianggeOrderId("T-DEC-004");
        assertTrue(updated.isPresent());
        assertEquals(TianggeOrderStatus.CANCELLED, updated.get().getStatus());
        assertEquals(25, inventoryService.getItem("P100").getStock());
    }

    @Test
    @DisplayName("DeliveryReceivedEvent resolves pending BACKORDERED items to ACCEPTED")
    void testDeliveryReceivedFulfillsBackorders() {
        supplierGateway.reorderProduct("P300", 10);

        TianggeOrder backorder = new TianggeOrder("T-DEC-005", "P300", 5, TianggeOrderStatus.PENDING);
        tianggeOrderRepository.saveAndFlush(backorder);
        decisionEngine.evaluateAndProcessOrder(backorder);

        assertEquals(TianggeOrderStatus.BACKORDERED, backorder.getStatus());

        // Restock product P300 directly to simulate delivery
        inventoryService.restock("P300", 10);

        // Fire delivery received event
        eventListener.onDeliveryReceived(new DeliveryReceivedEvent("P300", 10, "PO-TEST-100"));

        Optional<TianggeOrder> updated = tianggeOrderRepository.findByTianggeOrderId("T-DEC-005");
        assertTrue(updated.isPresent());
        assertEquals(TianggeOrderStatus.ACCEPTED, updated.get().getStatus());
        assertEquals(5, inventoryService.getItem("P300").getStock());
    }
}
