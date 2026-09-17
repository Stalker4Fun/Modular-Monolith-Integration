package edu.cit.valendez.notification;

import edu.cit.valendez.events.LowStockEvent;
import edu.cit.valendez.events.OrderCancelledEvent;
import edu.cit.valendez.events.OrderPlacedEvent;
import edu.cit.valendez.events.OrderRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event listener in the Notification module.
 * Depends EXCLUSIVELY on domain event classes from edu.cit.valendez.events.
 * Never imports or calls OrderService or InventoryService.
 */
@Component
class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationRepository notificationRepository;

    NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        String msg = String.format("Order O%d confirmed (%d items reserved)",
                event.getOrderId(),
                event.getItems() != null ? event.getItems().size() : 1);
        log.info("[Notification] {}", msg);
        notificationRepository.save(new Notification(msg));
    }

    @EventListener
    @Transactional
    public void onOrderRejected(OrderRejectedEvent event) {
        String msg = String.format("Order O%d rejected: %s",
                event.getOrderId(),
                event.getReason() != null ? event.getReason() : "Validation failed");
        log.info("[Notification] {}", msg);
        notificationRepository.save(new Notification(msg));
    }

    @EventListener
    @Transactional
    public void onOrderCancelled(OrderCancelledEvent event) {
        String msg = String.format("Order O%d cancelled - all reserved items restocked",
                event.getOrderId());
        log.info("[Notification] {}", msg);
        notificationRepository.save(new Notification(msg));
    }

    @EventListener
    @Transactional
    public void onLowStock(LowStockEvent event) {
        String msg = String.format("Low-stock alert: Reorder needed for %s (%s) - only %d units remaining (threshold: %d)",
                event.getProductName(),
                event.getProductId(),
                event.getRemainingStock(),
                event.getThreshold());
        log.warn("[Notification] {}", msg);
        notificationRepository.save(new Notification(msg));
    }
}

