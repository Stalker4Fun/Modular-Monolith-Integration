package edu.cit.valendez.tiangge;

import edu.cit.valendez.inventory.InventoryItemDto;
import edu.cit.valendez.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles Tiangge seller application initialization, heartbeats, and initial listings/stock publication.
 * Package-private to enforce module isolation.
 */
@Component
class TianggeStartupService {

    private static final Logger log = LoggerFactory.getLogger(TianggeStartupService.class);

    private final TianggeClient tianggeClient;
    private final InventoryService inventoryService;

    public TianggeStartupService(TianggeClient tianggeClient, InventoryService inventoryService) {
        this.tianggeClient = tianggeClient;
        this.inventoryService = inventoryService;
    }

    /**
     * Executes initial heartbeat, listings publication, and stock publication on application ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[Tiangge Startup] Registering live instance & publishing seller listings...");
        tianggeClient.sendHeartbeat();
        tianggeClient.publishListings();
        publishCurrentInventoryStock();
    }

    /**
     * Sends periodic heartbeat every 30 seconds to maintain active seller status.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void sendHeartbeatSchedule() {
        log.debug("[Tiangge Heartbeat] Sending periodic 30s heartbeat...");
        tianggeClient.sendHeartbeat();
    }

    private void publishCurrentInventoryStock() {
        List<InventoryItemDto> items = inventoryService.getAllItems();
        if (items == null || items.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            InventoryItemDto item = items.get(i);
            sb.append(String.format("{\"sellerSku\":\"%s\",\"available\":%d}", item.getProductId(), item.getStock()));
            if (i < items.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");

        log.info("[Tiangge Startup] Publishing current stock to Tiangge API: {}", sb);
        tianggeClient.publishBulkStock(sb.toString());
    }
}
