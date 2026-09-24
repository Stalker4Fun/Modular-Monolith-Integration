package edu.cit.valendez.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background tasks for Supplier ACL:
 * - Periodically retries pending purchase order transmissions to LegacySupply.
 * - Periodically polls active purchase order status until delivered.
 */
@Component
class SupplierOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderScheduler.class);
    private final SupplierGateway supplierGateway;

    SupplierOrderScheduler(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @Scheduled(fixedDelayString = "${supplier.sync.interval-ms:15000}")
    public void runSupplierSync() {
        try {
            supplierGateway.syncOrderStatus();
        } catch (Exception e) {
            log.warn("[Supplier Scheduler] Periodic sync cycle encountered error: {}", e.getMessage());
        }
    }
}

