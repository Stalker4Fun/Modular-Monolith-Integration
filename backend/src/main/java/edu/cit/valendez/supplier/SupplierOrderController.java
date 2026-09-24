package edu.cit.valendez.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier")
public class SupplierOrderController {

    private final SupplierGateway supplierGateway;

    public SupplierOrderController(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<SupplierOrderDto>> getAllSupplierOrders() {
        return ResponseEntity.ok(supplierGateway.getAllSupplierOrders());
    }

    @PostMapping("/reorder/{productId}")
    public ResponseEntity<SupplierOrderDto> triggerReorder(
            @PathVariable String productId,
            @RequestParam(defaultValue = "10") int quantity) {
        SupplierOrderDto dto = supplierGateway.reorderProduct(productId, quantity);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> triggerSync() {
        supplierGateway.syncOrderStatus();
        return ResponseEntity.ok(Map.of("message", "Supplier order synchronization completed successfully."));
    }
}

