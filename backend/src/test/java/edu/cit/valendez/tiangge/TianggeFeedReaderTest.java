package edu.cit.valendez.tiangge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TianggeFeedReaderTest {

    @Autowired
    private FeedCursorService feedCursorService;

    @Autowired
    private FeedCursorRepository feedCursorRepository;

    @Autowired
    private TianggeOrderRepository tianggeOrderRepository;

    @Autowired
    private TianggeProperties tianggeProperties;

    @Autowired
    private TianggeDecisionEngine decisionEngine;

    private TianggeFeedReaderService feedReaderService;

    @BeforeEach
    void setUp() {
        // Mock client returning empty list by default
        TianggeClient mockClient = new TianggeClient(tianggeProperties) {
            @Override
            public List<TianggeFeedEventDto> fetchFeed(long sinceEventId) {
                return List.of();
            }
        };
        feedReaderService = new TianggeFeedReaderService(mockClient, feedCursorService, tianggeOrderRepository, decisionEngine);
    }

    @Test
    @DisplayName("FeedCursorService reads stored last_event_id on startup and does not reset to zero")
    void testCursorPersistenceAndResume() {
        feedCursorService.updateCursor(42L);

        assertEquals(42L, feedCursorService.getCurrentCursor());

        // Simulating app restart check: fetching from repo directly
        Optional<FeedCursor> cursorOpt = feedCursorRepository.findById(1L);
        assertTrue(cursorOpt.isPresent());
        assertEquals(42L, cursorOpt.get().getLastEventId());
    }

    @Test
    @DisplayName("FeedReader processEvent ingests new order and updates cursor")
    void testProcessEventIngestsOrderAndUpdateCursor() {
        TianggeFeedEventDto event = new TianggeFeedEventDto(100L, "ORDER_PLACED", "T-ORD-100", "P100", 2);

        feedReaderService.processEvent(event);

        assertTrue(tianggeOrderRepository.existsByTianggeOrderId("T-ORD-100"));
        assertEquals(100L, feedCursorService.getCurrentCursor());
    }

    @Test
    @DisplayName("FeedReader processEvent skips duplicate orders gracefully and maintains cursor")
    void testProcessEventDeduplication() {
        TianggeFeedEventDto event1 = new TianggeFeedEventDto(101L, "ORDER_PLACED", "T-ORD-101", "P100", 1);
        feedReaderService.processEvent(event1);

        assertEquals(101L, feedCursorService.getCurrentCursor());
        assertEquals(1, tianggeOrderRepository.count());

        // Duplicate event with higher eventId but same tiangge_order_id
        TianggeFeedEventDto duplicateEvent = new TianggeFeedEventDto(105L, "ORDER_PLACED", "T-ORD-101", "P100", 1);
        assertDoesNotThrow(() -> feedReaderService.processEvent(duplicateEvent));

        // Order count remains 1, cursor updated to 105
        assertEquals(1, tianggeOrderRepository.count());
        assertEquals(105L, feedCursorService.getCurrentCursor());
    }

    @Test
    @DisplayName("TianggeProperties carries live instance ID configuration")
    void testInstanceIdConfigured() {
        assertNotNull(tianggeProperties.getInstanceId());
        assertFalse(tianggeProperties.getInstanceId().isBlank());
    }
}
