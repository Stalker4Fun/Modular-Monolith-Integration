package edu.cit.valendez.supplier;

import edu.cit.valendez.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Package-private implementation of SupplierGateway interface.
 * Implements Anti-Corruption Layer logic for LegacySupply integration.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

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

        SupplierProductMapper.ProductMapping mapping = productMapper.getMapping(productId);
        int cases = productMapper.calculateCases(productId, targetQuantity);
        int totalUnits = cases * mapping.getPackSize();

        String requestId = UUID.randomUUID().toString();
        String tempRef = "RO-TEMP-" + UUID.randomUUID().toString().substring(0, 8);

        SupplierOrder order = new SupplierOrder(
                productId.trim(),
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
                // Pending submission - retry placement
                if (order.getRetryCount() < 3) {
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
            
            // Mark as FAILED if unrecoverable client error (e.g. 422 invalid SKU)
            if ("E-SKU-02".equals(e.getErrorCode()) || "E-QTY-11".equals(e.getErrorCode())) {
                order.setStatus(SupplierOrderStatus.FAILED);
            } else {
                order.setStatus(SupplierOrderStatus.PENDING);
            }
            log.warn("[ACL Supplier] Order placement failed (attempt {}): {}", order.getRetryCount(), e.getMessage());
        }
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

