package edu.cit.valendez.shop;

import edu.cit.valendez.inventory.InventoryItemDto;
import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.inventory.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("placeOrder confirms order and persists CONFIRMED when inventory reservation succeeds")
    void testPlaceOrderConfirmed() {
        InventoryItemDto remaining = new InventoryItemDto("P100", "Wireless Mouse", 23);
        ReservationResult reservationResult = ReservationResult.confirmed(remaining);

        when(inventoryService.reserve("P100", 2)).thenReturn(reservationResult);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setOrderId(1L);
            return o;
        });

        OrderRequest request = new OrderRequest("P100", 2);
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals(1L, response.getOrderId());
        assertEquals("CONFIRMED", response.getStatus());
        assertNull(response.getReason());
        assertNotNull(response.getInventory());
        assertEquals(23, response.getInventory().getStock());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();
        assertEquals("P100", savedOrder.getProductId());
        assertEquals(2, savedOrder.getQuantity());
        assertEquals("CONFIRMED", savedOrder.getStatus());
        assertNull(savedOrder.getReason());
    }

    @Test
    @DisplayName("placeOrder rejects order and persists REJECTED when inventory reservation fails")
    void testPlaceOrderRejected() {
        InventoryItemDto currentStock = new InventoryItemDto("P300", "USB-C Hub", 0);
        ReservationResult reservationResult = ReservationResult.rejected("Insufficient stock: requested 1, available 0", currentStock);

        when(inventoryService.reserve("P300", 1)).thenReturn(reservationResult);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setOrderId(2L);
            return o;
        });

        OrderRequest request = new OrderRequest("P300", 1);
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals(2L, response.getOrderId());
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Insufficient stock: requested 1, available 0", response.getReason());
        assertNotNull(response.getInventory());
        assertEquals(0, response.getInventory().getStock());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();
        assertEquals("P300", savedOrder.getProductId());
        assertEquals(1, savedOrder.getQuantity());
        assertEquals("REJECTED", savedOrder.getStatus());
        assertEquals("Insufficient stock: requested 1, available 0", savedOrder.getReason());
    }
}

