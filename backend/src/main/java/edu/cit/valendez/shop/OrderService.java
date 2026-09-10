package edu.cit.valendez.shop;

import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.inventory.ReservationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * OrderService in the Order module (edu.cit.valendez.shop).
 * Demonstrates in-process modular monolith integration:
 * - Depends strictly on the public InventoryService interface.
 * - Injected via constructor by Spring IoC container.
 * - Does not and cannot know about the package-private InventoryServiceImpl.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Order request must not be null");
        }

        String productId = request.getProductId();
        int quantity = request.getQuantity();

        // 1. Call InventoryService in-process across the module boundary
        ReservationResult reservation = inventoryService.reserve(productId, quantity);

        String status = reservation.isSuccess() ? "CONFIRMED" : "REJECTED";
        String reason = reservation.getReason();

        // 2. Persist the result in the orders table
        Order order = new Order(productId, quantity, status, reason);
        Order savedOrder = orderRepository.save(order);

        // 3. Return response with status, reason, and inventory snapshot
        return new OrderResponse(savedOrder.getOrderId(), status, reason, reservation.getInventory());
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}

