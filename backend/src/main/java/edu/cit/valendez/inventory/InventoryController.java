package edu.cit.valendez.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<InventoryItemDto>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItemDto> getInventoryItem(@PathVariable String productId) {
        InventoryItemDto item = inventoryService.getItem(productId);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }
}

