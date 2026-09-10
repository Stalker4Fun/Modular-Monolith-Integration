package edu.cit.valendez.shop;

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

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    @DisplayName("POST /api/orders returns 200 OK with CONFIRMED status")
    void testPlaceOrderConfirmedEndpoint() throws Exception {
        InventoryItemDto remaining = new InventoryItemDto("P100", "Wireless Mouse", 23);
        OrderResponse response = new OrderResponse(1L, "CONFIRMED", null, remaining);

        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(response);

        String jsonRequest = "{\"productId\":\"P100\",\"quantity\":2}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.inventory.productId").value("P100"))
                .andExpect(jsonPath("$.inventory.stock").value(23));
    }

    @Test
    @DisplayName("POST /api/orders returns 200 OK with REJECTED status and reason")
    void testPlaceOrderRejectedEndpoint() throws Exception {
        InventoryItemDto current = new InventoryItemDto("P300", "USB-C Hub", 0);
        OrderResponse response = new OrderResponse(2L, "REJECTED", "Insufficient stock: requested 1, available 0", current);

        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(response);

        String jsonRequest = "{\"productId\":\"P300\",\"quantity\":1}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Insufficient stock: requested 1, available 0"))
                .andExpect(jsonPath("$.inventory.productId").value("P300"))
                .andExpect(jsonPath("$.inventory.stock").value(0));
    }

    @Test
    @DisplayName("GET /api/orders returns order list")
    void testGetOrdersEndpoint() throws Exception {
        Order order = new Order("P100", 2, "CONFIRMED", null);
        order.setOrderId(10L);
        when(orderService.getAllOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[0].productId").value("P100"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }
}

