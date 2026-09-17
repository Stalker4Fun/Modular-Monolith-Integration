package edu.cit.valendez.shop;

import edu.cit.valendez.events.OrderCancelledEvent;
import edu.cit.valendez.events.OrderItemDto;
import edu.cit.valendez.events.OrderPlacedEvent;
import edu.cit.valendez.events.OrderRejectedEvent;
import edu.cit.valendez.inventory.InventoryItemDto;
import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.inventory.ReservationResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * OrderService in the Order module (edu.cit.valendez.shop).
 * Demonstrates in-process modular monolith integration:
 * - Depends strictly on the public InventoryService interface.
 * - Publishes domain events via Spring's ApplicationEventPublisher.
 * - Does not import or depend on the Notification module.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("Order request must not be null");
        }

        List<OrderItemRequest> itemRequests = request.getItems();
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }

        // Aggregate total requested quantity per product ID to validate combined demand
        Map<String, Integer> productTotals = new LinkedHashMap<>();
        for (OrderItemRequest item : itemRequests) {
            if (item == null) {
                throw new InvalidOrderException("Order items cannot be null");
            }
            String pId = item.getProductId() == null ? "" : item.getProductId().trim();
            if (pId.isEmpty()) {
                throw new InvalidOrderException("Product ID cannot be blank");
            }
            if (item.getQuantity() <= 0) {
                throw new InvalidOrderException("Quantity must be greater than 0");
            }
            productTotals.put(pId, productTotals.getOrDefault(pId, 0) + item.getQuantity());
        }

        // 1. All-or-Nothing Pre-Validation: Validate EVERY line item against current stock before reserving anything
        boolean validationFailed = false;
        String firstFailureReason = null;
        Map<String, String> itemFailures = new HashMap<>();

        for (Map.Entry<String, Integer> entry : productTotals.entrySet()) {
            String productId = entry.getKey();
            int requestedQuantity = entry.getValue();

            InventoryItemDto currentItem = inventoryService.getItem(productId);
            if (currentItem == null) {
                validationFailed = true;
                String msg = "Product not found: " + productId;
                itemFailures.put(productId, msg);
                if (firstFailureReason == null) firstFailureReason = msg;
            } else if (currentItem.getStock() < requestedQuantity) {
                validationFailed = true;
                String msg = String.format("Insufficient stock for %s (%s): requested %d, available %d",
                        currentItem.getName(), productId, requestedQuantity, currentItem.getStock());
                itemFailures.put(productId, msg);
                if (firstFailureReason == null) firstFailureReason = msg;
            }
        }

        // 2. If ANY item fails validation, REJECT the entire order - NO items reserved (all-or-nothing rollback)
        if (validationFailed) {
            Order order = new Order("REJECTED", firstFailureReason);
            if (!itemRequests.isEmpty()) {
                order.setProductId(itemRequests.get(0).getProductId());
                order.setQuantity(itemRequests.get(0).getQuantity());
            }

            List<OrderItemDto> itemDtos = new ArrayList<>();
            for (OrderItemRequest itemReq : itemRequests) {
                String pId = itemReq.getProductId() != null ? itemReq.getProductId().trim() : "";
                String failMsg = itemFailures.get(pId);
                String outcome = failMsg != null ? "REJECTED: " + failMsg : "REJECTED: Order rolled back due to failure on other item";
                itemDtos.add(new OrderItemDto(pId, itemReq.getQuantity(), outcome));
                order.addItem(new OrderItem(null, pId, itemReq.getQuantity()));
            }

            Order savedOrder = orderRepository.save(order);

            // Publish OrderRejectedEvent via Spring ApplicationEventPublisher
            eventPublisher.publishEvent(new OrderRejectedEvent(savedOrder.getOrderId(), firstFailureReason, itemDtos));

            return new OrderResponse(savedOrder.getOrderId(), "REJECTED", firstFailureReason, itemDtos, inventoryService.getAllItems());
        }

        // 3. All items passed validation -> Execute atomic reservations for each item
        List<OrderItemDto> itemDtos = new ArrayList<>();
        Order order = new Order("CONFIRMED", null);
        if (!itemRequests.isEmpty()) {
            order.setProductId(itemRequests.get(0).getProductId());
            order.setQuantity(itemRequests.get(0).getQuantity());
        }

        for (OrderItemRequest itemReq : itemRequests) {
            String pId = itemReq.getProductId().trim();
            ReservationResult res = inventoryService.reserve(pId, itemReq.getQuantity());
            if (!res.isSuccess()) {
                // Unexpected runtime failure during reserve - trigger transactional rollback
                throw new IllegalStateException("Unexpected reservation failure for product " + pId + ": " + res.getReason());
            }
            itemDtos.add(new OrderItemDto(pId, itemReq.getQuantity(), "CONFIRMED"));
            order.addItem(new OrderItem(null, pId, itemReq.getQuantity()));
        }

        Order savedOrder = orderRepository.save(order);

        // Publish OrderPlacedEvent via Spring ApplicationEventPublisher
        eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder.getOrderId(), itemDtos));

        return new OrderResponse(savedOrder.getOrderId(), "CONFIRMED", null, itemDtos, inventoryService.getAllItems());
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        if (orderId == null) {
            throw new InvalidOrderException("Order ID must not be null");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order with ID " + orderId + " does not exist"));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new OrderConflictException("Order #" + orderId + " is already CANCELLED");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new OrderConflictException("Cannot cancel order #" + orderId + " with status: " + order.getStatus());
        }

        // Set status to CANCELLED
        order.setStatus("CANCELLED");
        order.setReason("Cancelled by user - items restocked");

        List<OrderItemDto> itemDtos = new ArrayList<>();

        // Restock all reserved line items back to inventory
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (OrderItem item : order.getItems()) {
                ReservationResult restock = inventoryService.restock(item.getProductId(), item.getQuantity());
                if (!restock.isSuccess()) {
                    throw new IllegalStateException("Could not restock product " + item.getProductId()
                            + ": " + restock.getReason());
                }
                itemDtos.add(new OrderItemDto(item.getProductId(), item.getQuantity(), "RESTOCKED"));
            }
        } else if (order.getProductId() != null && order.getQuantity() != null && order.getQuantity() > 0) {
            // Fallback for legacy single-item orders
            ReservationResult restock = inventoryService.restock(order.getProductId(), order.getQuantity());
            if (!restock.isSuccess()) {
                throw new IllegalStateException("Could not restock product " + order.getProductId()
                        + ": " + restock.getReason());
            }
            itemDtos.add(new OrderItemDto(order.getProductId(), order.getQuantity(), "RESTOCKED"));
        }

        Order savedOrder = orderRepository.save(order);

        // Publish OrderCancelledEvent
        eventPublisher.publishEvent(new OrderCancelledEvent(savedOrder.getOrderId(), itemDtos));

        return new OrderResponse(savedOrder.getOrderId(), "CANCELLED", "Order successfully cancelled and items restocked", itemDtos, inventoryService.getAllItems());
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
