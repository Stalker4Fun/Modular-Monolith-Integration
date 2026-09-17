package edu.cit.valendez.inventory;

import edu.cit.valendez.events.LowStockEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Package-private implementation of InventoryService.
 * Enforces the modular monolith architectural boundary:
 * the Order module cannot see or directly instantiate this class,
 * and interacts exclusively via the public InventoryService interface.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    public static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(InventoryRepository inventoryRepository, ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemDto getItem(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            return null;
        }
        return inventoryRepository.findById(productId.trim())
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (productId == null || productId.trim().isEmpty()) {
            return ReservationResult.rejected("Product ID cannot be empty", null);
        }
        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be greater than 0", null);
        }

        Optional<InventoryItem> optionalItem = inventoryRepository.findById(productId.trim());
        if (optionalItem.isEmpty()) {
            return ReservationResult.rejected("Product not found: " + productId, null);
        }

        InventoryItem item = optionalItem.get();
        if (item.getStock() < quantity) {
            return ReservationResult.rejected(
                    String.format("Insufficient stock: requested %d, available %d", quantity, item.getStock()),
                    toDto(item)
            );
        }

        // Deduct inventory and persist
        item.setStock(item.getStock() - quantity);
        InventoryItem savedItem = inventoryRepository.save(item);

        // Low-stock auto-reorder rule: publish LowStockEvent if remaining stock drops below threshold (5)
        if (savedItem.getStock() < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new LowStockEvent(
                    savedItem.getProductId(),
                    savedItem.getName(),
                    savedItem.getStock(),
                    LOW_STOCK_THRESHOLD
            ));
        }

        return ReservationResult.confirmed(toDto(savedItem));
    }

    @Override
    @Transactional
    public ReservationResult restock(String productId, int quantity) {
        if (productId == null || productId.trim().isEmpty()) {
            return ReservationResult.rejected("Product ID cannot be empty", null);
        }
        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be greater than 0", null);
        }

        Optional<InventoryItem> optionalItem = inventoryRepository.findById(productId.trim());
        if (optionalItem.isEmpty()) {
            return ReservationResult.rejected("Product not found: " + productId, null);
        }

        InventoryItem item = optionalItem.get();
        item.setStock(item.getStock() + quantity);
        InventoryItem savedItem = inventoryRepository.save(item);

        return ReservationResult.confirmed(toDto(savedItem));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDto> getAllItems() {
        return inventoryRepository.findAll(Sort.by("productId")).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private InventoryItemDto toDto(InventoryItem item) {
        return new InventoryItemDto(item.getProductId(), item.getName(), item.getStock());
    }
}
