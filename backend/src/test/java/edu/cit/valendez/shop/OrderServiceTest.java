package edu.cit.valendez.shop;

import edu.cit.valendez.events.OrderPlacedEvent;
import edu.cit.valendez.events.OrderRejectedEvent;
import edu.cit.valendez.inventory.InventoryItemDto;
import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.inventory.ReservationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private InventoryService inventoryService;
    @Mock private OrderRepository orderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private OrderService orderService;

    @Test
    @DisplayName("a multi-item order reserves every line only after all stock checks succeed")
    void confirmsMultiItemOrder() {
        InventoryItemDto mouse = new InventoryItemDto("P100", "Wireless Mouse", 25);
        InventoryItemDto keyboard = new InventoryItemDto("P200", "Mechanical Keyboard", 10);
        when(inventoryService.getItem("P100")).thenReturn(mouse);
        when(inventoryService.getItem("P200")).thenReturn(keyboard);
        when(inventoryService.reserve("P100", 2)).thenReturn(ReservationResult.confirmed(
                new InventoryItemDto("P100", "Wireless Mouse", 23)));
        when(inventoryService.reserve("P200", 3)).thenReturn(ReservationResult.confirmed(
                new InventoryItemDto("P200", "Mechanical Keyboard", 7)));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItemDto("P100", "Wireless Mouse", 23),
                new InventoryItemDto("P200", "Mechanical Keyboard", 7)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(1L);
            return order;
        });

        OrderResponse response = orderService.placeOrder(new OrderRequest(List.of(
                new OrderItemRequest("P100", 2), new OrderItemRequest("P200", 3))));

        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(2, response.getItems().size());
        assertEquals(2, response.getInventory().size());
        verify(inventoryService).reserve("P100", 2);
        verify(inventoryService).reserve("P200", 3);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(2, orderCaptor.getValue().getItems().size());
    }

    @Test
    @DisplayName("a failed stock check rejects the entire order before any reservation")
    void rejectsWithoutPartialReservation() {
        when(inventoryService.getItem("P100")).thenReturn(new InventoryItemDto("P100", "Wireless Mouse", 25));
        when(inventoryService.getItem("P300")).thenReturn(new InventoryItemDto("P300", "USB-C Hub", 0));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItemDto("P100", "Wireless Mouse", 25),
                new InventoryItemDto("P300", "USB-C Hub", 0)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId(2L);
            return order;
        });

        OrderResponse response = orderService.placeOrder(new OrderRequest(List.of(
                new OrderItemRequest("P100", 2), new OrderItemRequest("P300", 1))));

        assertEquals("REJECTED", response.getStatus());
        assertTrue(response.getReason().contains("Insufficient stock"));
        assertEquals(2, response.getItems().size());
        assertTrue(response.getItems().get(0).getOutcome().contains("rolled back"));
        verify(inventoryService, never()).reserve(anyString(), anyInt());
        verify(eventPublisher).publishEvent(any(OrderRejectedEvent.class));
    }

    @Test
    @DisplayName("cancelling a confirmed order restocks each line and changes its status")
    void cancelsAndRestocksConfirmedOrder() {
        Order order = new Order("CONFIRMED", null);
        order.setOrderId(9L);
        order.addItem(new OrderItem(null, "P100", 2));
        order.addItem(new OrderItem(null, "P200", 1));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        when(inventoryService.restock("P100", 2)).thenReturn(ReservationResult.confirmed(null));
        when(inventoryService.restock("P200", 1)).thenReturn(ReservationResult.confirmed(null));
        when(inventoryService.getAllItems()).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(9L);

        assertEquals("CANCELLED", response.getStatus());
        assertEquals(2, response.getItems().size());
        assertEquals("CANCELLED", order.getStatus());
        verify(inventoryService).restock("P100", 2);
        verify(inventoryService).restock("P200", 1);
    }

    @Test
    @DisplayName("cancellation distinguishes a missing order from an already-cancelled order")
    void rejectsInvalidCancellationStates() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(404L));

        Order cancelled = new Order("CANCELLED", "Cancelled by user - items restocked");
        cancelled.setOrderId(8L);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(cancelled));

        assertThrows(OrderConflictException.class, () -> orderService.cancelOrder(8L));
        verify(inventoryService, never()).restock(anyString(), anyInt());
    }
}
