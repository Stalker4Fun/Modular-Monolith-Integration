package edu.cit.valendez.supplier;

import edu.cit.valendez.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Package-private implementation of SupplierGateway interface.
 * Implements Anti-Corruption Layer logic for LegacySupply integration.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);
    private static final List<SupplierOrderStatus> OPEN_ORDER_STATUSES = List.of(
            SupplierOrderStatus.PENDING,
            SupplierOrderStatus.ACCEPTED,
            SupplierOrderStatus.PICKING,
            SupplierOrderStatus.SHIPPED
    );

    private final SupplierOrderRepository repository;
    private final SupplierProductMapper productMapper;
    private final LegacySupplyClient legacySupplyClient;
    private final ApplicationEventPublisher eventPublisher;

    SupplierGatewayImpl(SupplierOrderRepository repository,
                        SupplierProductMapper productMapper,
                        LegacySupplyClient legacySupplyClient,
                        ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.productMapper = productMapper;
        this.legacySupplyClient = legacySupplyClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SupplierOrderDto reorderProduct(String productId, int targetQuantity) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        String cleanProductId = productId.trim();
        Optional<SupplierOrder> existingOpenOrder = repository
                .findFirstByProductIdAndStatusInOrderByCreatedAtDesc(cleanProductId, OPEN_ORDER_STATUSES);
        if (existingOpenOrder.isPresent()) {
            SupplierOrder existing = existingOpenOrder.get();
            log.info("[ACL Supplier] Reusing open replenishment order {} for product {} to prevent a duplicate.",
                    existing.getId(), cleanProductId);
            return toDto(existing);
        }

        SupplierProductMapper.ProductMapping mapping = productMapper.getMapping(cleanProductId);
        int cases = productMapper.calculateCases(cleanProductId, targetQuantity);
        int totalUnits = cases * mapping.getPackSize();

        String requestId = UUID.randomUUID().toString();
        String tempRef = "RO-TEMP-" + UUID.randomUUID().toString().substring(0, 8);

        SupplierOrder order = new SupplierOrder(
                cleanProductId,
                tempRef,
                requestId,
                mapping.getSupplierSku(),
                cases,
                totalUnits
        );

        SupplierOrder savedOrder = repository.save(order);
        String finalBuyerRef = "RO-" + savedOrder.getId();
        savedOrder.setBuyerRef(finalBuyerRef);
        savedOrder = repository.save(savedOrder);

        // Execute initial placement attempt
        processOrderPlacement(savedOrder);

        return toDto(repository.save(savedOrder));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierOrderDto> getAllSupplierOrders() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void syncOrderStatus() {
        List<SupplierOrder> activeOrders = repository.findByStatusIn(Arrays.asList(
                SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED,
                SupplierOrderStatus.PENDING
        ));

        for (SupplierOrder order : activeOrders) {
            if (order.getStatus() == SupplierOrderStatus.PENDING && order.getPoNumber() == null) {
                // A 503/timeout may last longer than a few scheduler cycles.
                // Keep the durable order pending and replay its original,
                // idempotent request after backoff until it is accepted.
                if (order.isReadyForRetry(OffsetDateTime.now())) {
                    processOrderPlacement(order);
                    repository.save(order);
                }
                continue;
            }

            if (order.getPoNumber() != null) {
                try {
                    XmlPurchaseOrderStatus statusResp = legacySupplyClient.getOrderStatus(order.getPoNumber());
                    SupplierOrderStatus oldStatus = order.getStatus();
                    SupplierOrderStatus newStatus = SupplierOrderStatus.fromStatusCode(statusResp.getStatusCode());

                    if (oldStatus != newStatus) {
                        log.info("[ACL Supplier] Order {} status changed: {} -> {}", order.getPoNumber(), oldStatus, newStatus);
                        order.setStatus(newStatus);
                        
                        if (newStatus == SupplierOrderStatus.DELIVERED) {
                            log.info("[ACL Supplier] Order {} delivered. Publishing SupplierOrderDeliveredEvent for product {} ({} units)",
                                    order.getPoNumber(), order.getProductId(), order.getUnits());
                            eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                    order.getProductId(),
                                    order.getUnits(),
                                    order.getPoNumber()
                            ));
                        }
                    }
                    repository.save(order);
                } catch (LegacySupplyException e) {
                    log.warn("[ACL Supplier] Status sync failed for PO {}: {}", order.getPoNumber(), e.getMessage());
                }
            }
        }
    }

    private void processOrderPlacement(SupplierOrder order) {
        try {
            log.info("[ACL Supplier] Transmitting purchase order to LegacySupply: Ref={}, SKU={}, Cases={}",
                    order.getBuyerRef(), order.getSupplierSku(), order.getCases());

            XmlPurchaseOrderAck ack = legacySupplyClient.placeOrder(
                    order.getSupplierSku(),
                    order.getCases(),
                    order.getBuyerRef(),
                    order.getRequestId()
            );

            order.setPoNumber(ack.getPoNumber());
            SupplierOrderStatus status = SupplierOrderStatus.fromStatusCode(ack.getStatusCode());
            order.setStatus(status);
            order.setFailureReason(null);
            order.clearRetrySchedule();
            log.info("[ACL Supplier] Order acknowledged by LegacySupply. PO: {}, Status: {}", ack.getPoNumber(), status);

            if (status == SupplierOrderStatus.DELIVERED) {
                eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                        order.getProductId(),
                        order.getUnits(),
                        order.getPoNumber()
                ));
            }
        } catch (LegacySupplyException e) {
            order.incrementRetryCount();
            order.setFailureReason("[" + e.getErrorCode() + "] " + e.getMessage());
            
            // Do not retry malformed or contradictory requests.  Availability,
            // rate-limit, and transport errors remain PENDING for safe replay.
            if (isPermanentOrderError(e.getErrorCode())) {
                order.setStatus(SupplierOrderStatus.FAILED);
                order.clearRetrySchedule();
            } else {
                order.setStatus(SupplierOrderStatus.PENDING);
                order.scheduleRetry(OffsetDateTime.now());
            }
            log.warn("[ACL Supplier] Order placement failed (attempt {}): {}", order.getRetryCount(), e.getMessage());
        }
    }

    @Override
    public boolean isSupplierAvailable() {
        return legacySupplyClient.isAvailable();
    }

    private boolean isPermanentOrderError(String errorCode) {
        return "E-SKU-02".equals(errorCode)
                || "E-QTY-11".equals(errorCode)
                || "E-REF-05".equals(errorCode)
                || "E-IDEM-04".equals(errorCode)
                || "E-FMT-01".equals(errorCode)
                || "E-FMT-02".equals(errorCode);
    }

    private SupplierOrderDto toDto(SupplierOrder entity) {
        return new SupplierOrderDto(
                entity.getId(),
                entity.getProductId(),
                entity.getBuyerRef(),
                entity.getRequestId(),
                entity.getPoNumber(),
                entity.getSupplierSku(),
                entity.getCases(),
                entity.getUnits(),
                entity.getStatus() != null ? entity.getStatus().name() : "PENDING",
                entity.getFailureReason(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

