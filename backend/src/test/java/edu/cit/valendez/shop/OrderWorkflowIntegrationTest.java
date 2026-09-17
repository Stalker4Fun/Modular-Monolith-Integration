package edu.cit.valendez.shop;

import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OrderWorkflowIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private NotificationService notificationService;

    @Test
    @DisplayName("confirmed multi-item order reserves all lines and emits confirmation plus low-stock notifications")
    void confirmedOrderReservesEverythingAndEmitsEvents() {
        OrderResponse response = orderService.placeOrder(new OrderRequest(List.of(
                new OrderItemRequest("P100", 2), new OrderItemRequest("P200", 6))));

        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(2, response.getItems().size());
        assertEquals(23, inventoryService.getItem("P100").getStock());
        assertEquals(4, inventoryService.getItem("P200").getStock());
        assertEquals(2, orderRepository.findById(response.getOrderId()).orElseThrow().getItems().size());
        assertTrue(notificationService.getRecentNotifications().stream()
                .anyMatch(notification -> notification.getMessage().contains("confirmed")));
        assertTrue(notificationService.getRecentNotifications().stream()
                .anyMatch(notification -> notification.getMessage().contains("Reorder needed for Mechanical Keyboard")));
    }

    @Test
    @DisplayName("one failed line rejects the full order and leaves every product unchanged")
    void rejectedOrderDoesNotPartiallyReserveInventory() {
        OrderResponse response = orderService.placeOrder(new OrderRequest(List.of(
                new OrderItemRequest("P100", 2), new OrderItemRequest("P300", 1))));

        assertEquals("REJECTED", response.getStatus());
        assertEquals(25, inventoryService.getItem("P100").getStock());
        assertEquals(0, inventoryService.getItem("P300").getStock());
        assertEquals(2, orderRepository.findById(response.getOrderId()).orElseThrow().getItems().size());
        assertTrue(notificationService.getRecentNotifications().stream()
                .anyMatch(notification -> notification.getMessage().contains("rejected")));
    }

    @Test
    @DisplayName("cancelling a confirmed order restores all reserved inventory")
    void cancellationRestocksAllItems() {
        OrderResponse placed = orderService.placeOrder(new OrderRequest(List.of(
                new OrderItemRequest("P100", 2), new OrderItemRequest("P200", 3))));
        assertEquals(23, inventoryService.getItem("P100").getStock());
        assertEquals(7, inventoryService.getItem("P200").getStock());

        OrderResponse cancelled = orderService.cancelOrder(placed.getOrderId());

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals(25, inventoryService.getItem("P100").getStock());
        assertEquals(10, inventoryService.getItem("P200").getStock());
        assertEquals("CANCELLED", orderRepository.findById(placed.getOrderId()).orElseThrow().getStatus());
        assertTrue(notificationService.getRecentNotifications().stream()
                .anyMatch(notification -> notification.getMessage().contains("cancelled")));
    }
}
