package edu.cit.valendez.shop;

import edu.cit.valendez.events.OrderItemDto;
import edu.cit.valendez.inventory.InventoryItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;
    @Mock private OrderService orderService;
    @InjectMocks private OrderController orderController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    @DisplayName("POST /api/orders accepts multiple items and returns the typed order response")
    void placeMultiItemOrder() throws Exception {
        OrderResponse response = new OrderResponse(1L, "CONFIRMED", null,
                List.of(new OrderItemDto("P100", 2, "CONFIRMED"), new OrderItemDto("P200", 1, "CONFIRMED")),
                List.of(new InventoryItemDto("P100", "Wireless Mouse", 23)));
        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"P100\",\"quantity\":2},{\"productId\":\"P200\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[1].productId").value("P200"))
                .andExpect(jsonPath("$.inventory[0].stock").value(23));
    }

    @Test
    @DisplayName("POST /api/orders/{orderId}/cancel returns the cancelled order")
    void cancelOrder() throws Exception {
        when(orderService.cancelOrder(10L)).thenReturn(new OrderResponse(10L, "CANCELLED",
                "Order successfully cancelled and items restocked",
                List.of(new OrderItemDto("P100", 2, "RESTOCKED")), List.of()));

        mockMvc.perform(post("/api/orders/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].outcome").value("RESTOCKED"));
    }

    @Test
    @DisplayName("GET /api/orders returns order history with line items")
    void getOrders() throws Exception {
        Order order = new Order("CONFIRMED", null);
        order.setOrderId(10L);
        order.addItem(new OrderItem(null, "P100", 2));
        when(orderService.getAllOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[0].items[0].productId").value("P100"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }
}
