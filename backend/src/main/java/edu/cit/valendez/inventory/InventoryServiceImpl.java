package edu.cit.valendez.inventory;

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

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
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

