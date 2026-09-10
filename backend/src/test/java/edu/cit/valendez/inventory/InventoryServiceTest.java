package edu.cit.valendez.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private InventoryItem mouse;
    private InventoryItem keyboard;
    private InventoryItem hub;

    @BeforeEach
    void setUp() {
        mouse = new InventoryItem("P100", "Wireless Mouse", 25);
        keyboard = new InventoryItem("P200", "Mechanical Keyboard", 10);
        hub = new InventoryItem("P300", "USB-C Hub", 0);
    }

    @Test
    @DisplayName("getItem returns dto when product exists")
    void testGetItemSuccess() {
        when(inventoryRepository.findById("P100")).thenReturn(Optional.of(mouse));

        InventoryItemDto result = inventoryService.getItem("P100");

        assertNotNull(result);
        assertEquals("P100", result.getProductId());
        assertEquals("Wireless Mouse", result.getName());
        assertEquals(25, result.getStock());
    }

    @Test
    @DisplayName("reserve succeeds and decrements stock when stock is sufficient")
    void testReserveSuccess() {
        when(inventoryRepository.findById("P100")).thenReturn(Optional.of(mouse));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResult result = inventoryService.reserve("P100", 2);

        assertTrue(result.isSuccess());
        assertNull(result.getReason());
        assertNotNull(result.getInventory());
        assertEquals(23, result.getInventory().getStock());

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryRepository).save(captor.capture());
        assertEquals(23, captor.getValue().getStock());
    }

    @Test
    @DisplayName("reserve rejects when requested quantity exceeds available stock")
    void testReserveInsufficientStock() {
        when(inventoryRepository.findById("P300")).thenReturn(Optional.of(hub));

        ReservationResult result = inventoryService.reserve("P300", 1);

        assertFalse(result.isSuccess());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("Insufficient stock"));
        assertNotNull(result.getInventory());
        assertEquals(0, result.getInventory().getStock());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserve rejects when product does not exist")
    void testReserveProductNotFound() {
        when(inventoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        ReservationResult result = inventoryService.reserve("UNKNOWN", 1);

        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("Product not found"));
        assertNull(result.getInventory());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserve rejects when quantity is zero or negative")
    void testReserveInvalidQuantity() {
        ReservationResult result = inventoryService.reserve("P100", 0);

        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("Quantity must be greater than 0"));
        verify(inventoryRepository, never()).findById(any());
    }
}

