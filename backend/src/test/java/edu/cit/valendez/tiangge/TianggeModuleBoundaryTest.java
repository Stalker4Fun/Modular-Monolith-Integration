package edu.cit.valendez.tiangge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TianggeModuleBoundaryTest {

    @Autowired
    private FeedCursorRepository feedCursorRepository;

    @Autowired
    private TianggeOrderRepository tianggeOrderRepository;

    @Test
    @DisplayName("FeedCursor and TianggeOrder classes must be package-private for module isolation")
    void testClassesArePackagePrivate() {
        assertFalse(Modifier.isPublic(FeedCursor.class.getModifiers()), "FeedCursor MUST NOT be public!");
        assertFalse(Modifier.isPublic(TianggeOrder.class.getModifiers()), "TianggeOrder MUST NOT be public!");
        assertFalse(Modifier.isPublic(FeedCursorRepository.class.getModifiers()), "FeedCursorRepository MUST NOT be public!");
        assertFalse(Modifier.isPublic(TianggeOrderRepository.class.getModifiers()), "TianggeOrderRepository MUST NOT be public!");
        assertFalse(Modifier.isPublic(TianggeProperties.class.getModifiers()), "TianggeProperties MUST NOT be public!");
    }

    @Test
    @DisplayName("FeedCursor can be saved and retrieved accurately without resetting")
    void testFeedCursorPersistence() {
        FeedCursor cursor = new FeedCursor(1L, 105L);
        feedCursorRepository.save(cursor);

        Optional<FeedCursor> retrieved = feedCursorRepository.findById(1L);
        assertTrue(retrieved.isPresent());
        assertEquals(105L, retrieved.get().getLastEventId());
        assertNotNull(retrieved.get().getUpdatedAt());
    }

    @Test
    @DisplayName("TianggeOrder enforces UNIQUE constraint on tiangge_order_id")
    void testTianggeOrderUniqueConstraint() {
        TianggeOrder order1 = new TianggeOrder("T-ORD-001", "P100", 2, TianggeOrderStatus.ACCEPTED);
        tianggeOrderRepository.saveAndFlush(order1);

        TianggeOrder order2 = new TianggeOrder("T-ORD-001", "P100", 2, TianggeOrderStatus.ACCEPTED);
        assertThrows(DataIntegrityViolationException.class, () -> {
            tianggeOrderRepository.saveAndFlush(order2);
        });
    }

    @Test
    @DisplayName("TianggeProperties resolves live instance ID")
    void testTianggeProperties() {
        TianggeProperties props = new TianggeProperties(
                "https://tiangge-marketplace.onrender.com/api/v1",
                "21-3360-213",
                "secret-key"
        );
        assertEquals("21-3360-213", props.getInstanceId());
        assertEquals("https://tiangge-marketplace.onrender.com/api/v1", props.getBaseUrl());
        assertEquals("secret-key", props.getApiKey());
    }
}
