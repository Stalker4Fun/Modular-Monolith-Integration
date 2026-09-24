package edu.cit.valendez.supplier;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-Corruption Layer mapper that converts internal product IDs into LegacySupply catalog concepts
 * (SupplierSku, PackSize, Unit/Case conversion).
 */
@Component
class SupplierProductMapper {

    private final Map<String, ProductMapping> mappings = new ConcurrentHashMap<>();

    SupplierProductMapper() {
        // Real partner catalog mappings for Student ID 21-3360-213 from LegacySupply GET /catalog
        mappings.put("P100", new ProductMapping("ZTY-3082", 6));  // Wireless Mouse 2.4GHz (PackSize: 6)
        mappings.put("P200", new ProductMapping("ZTY-8985", 12)); // Keyboard Mech TKL (PackSize: 12)
        mappings.put("P300", new ProductMapping("ZTY-1364", 10)); // USB Hub 4-Port (PackSize: 10)
    }

    public ProductMapping getMapping(String productId) {
        if (productId == null) return new ProductMapping("SKU-GENERIC", 5);
        String cleanId = productId.trim();
        return mappings.computeIfAbsent(cleanId, id -> new ProductMapping("SKU-" + id, 5));
    }

    public void registerMapping(String productId, String supplierSku, int packSize) {
        if (productId != null && supplierSku != null && packSize > 0) {
            mappings.put(productId.trim(), new ProductMapping(supplierSku, packSize));
        }
    }

    /**
     * Calculates required cases to meet or exceed requested target units.
     */
    public int calculateCases(String productId, int targetUnits) {
        ProductMapping mapping = getMapping(productId);
        int packSize = Math.max(1, mapping.getPackSize());
        int neededUnits = Math.max(1, targetUnits);
        return (int) Math.ceil((double) neededUnits / packSize);
    }

    static class ProductMapping {
        private final String supplierSku;
        private final int packSize;

        public ProductMapping(String supplierSku, int packSize) {
            this.supplierSku = supplierSku;
            this.packSize = packSize;
        }

        public String getSupplierSku() {
            return supplierSku;
        }

        public int getPackSize() {
            return packSize;
        }
    }
}

