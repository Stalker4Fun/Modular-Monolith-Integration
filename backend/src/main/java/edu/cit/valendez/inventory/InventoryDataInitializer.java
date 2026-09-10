package edu.cit.valendez.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes required seed inventory data if the inventory table is empty.
 * In production Supabase PostgreSQL, this is idempotent and respects data already
 * seeded via schema.sql.
 */
@Component
class InventoryDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryDataInitializer.class);
    private final InventoryRepository inventoryRepository;

    InventoryDataInitializer(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        if (inventoryRepository.count() == 0) {
            log.info("Inventory table is empty. Seeding initial products (P100, P200, P300)...");
            inventoryRepository.saveAll(List.of(
                    new InventoryItem("P100", "Wireless Mouse", 25),
                    new InventoryItem("P200", "Mechanical Keyboard", 10),
                    new InventoryItem("P300", "USB-C Hub", 0)
            ));
            log.info("Initial inventory seed completed successfully.");
        } else {
            log.info("Inventory table already populated with {} items.", inventoryRepository.count());
        }
    }
}

