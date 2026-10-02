package edu.cit.valendez.tiangge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Scheduled job and event processor for reading Tiangge Marketplace order feeds.
 * Implements persistent cursor resume (using seq integer) and idempotency deduplication.
 * Package-private to enforce module isolation.
 */
@Service
class TianggeFeedReaderService {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedReaderService.class);

    private final TianggeClient tianggeClient;
    private final FeedCursorService feedCursorService;
    private final TianggeOrderRepository tianggeOrderRepository;
    private final TianggeDecisionEngine decisionEngine;

    public TianggeFeedReaderService(
            TianggeClient tianggeClient,
            FeedCursorService feedCursorService,
            TianggeOrderRepository tianggeOrderRepository,
            TianggeDecisionEngine decisionEngine
    ) {
        this.tianggeClient = tianggeClient;
        this.feedCursorService = feedCursorService;
        this.tianggeOrderRepository = tianggeOrderRepository;
        this.decisionEngine = decisionEngine;
    }

    /**
     * Polls the feed at configured intervals using the stored cursor position.
     * cursor = last processed seq (integer). Sends after=N to get events with seq > N.
     */
    @Scheduled(fixedDelayString = "${tiangge.poll-interval-ms:1000}")
    public void pollFeed() {
        long currentCursor = feedCursorService.getCurrentCursor();
        log.debug("[Tiangge Feed] Polling feed with cursor seq = {}", currentCursor);

        List<TianggeFeedEventDto> events = tianggeClient.fetchFeed(currentCursor);
        if (events == null || events.isEmpty()) {
            return;
        }

        // Sort events by seq ascending to process in strict order
        events.stream()
                .filter(e -> e.getSeq() != null && e.getSeq() > currentCursor)
                .sorted(Comparator.comparingLong(TianggeFeedEventDto::getSeq))
                .forEach(this::processEventSafely);
    }

    private void processEventSafely(TianggeFeedEventDto event) {
        try {
            processEvent(event);
        } catch (Exception e) {
            log.error("[Tiangge Feed] Error processing event seq={}: {}", event.getSeq(), e.getMessage(), e);
        }
    }

    /**
     * Processes a single feed event, enforcing deduplication and persistent cursor updates.
     * Uses seq (integer) as the cursor value; orderId is the business dedup key.
     */
    @Transactional
    public void processEvent(TianggeFeedEventDto event) {
        Long seq = event.getSeq();
        String orderId = event.getOrderId();
        String type = event.getEventType() != null ? event.getEventType().toUpperCase() : "";

        if (seq == null || orderId == null || orderId.isBlank()) {
            log.warn("[Tiangge Feed] Skipping malformed feed event (missing seq or orderId): {}", event);
            return;
        }

        // Check if event is a cancellation event
        if (type.contains("CANCEL")) {
            log.info("[Tiangge Feed] Processing cancellation event seq={} for orderId {}", seq, orderId);
            decisionEngine.processCancellation(orderId);
            feedCursorService.updateCursor(seq);
            return;
        }

        // Deduplication check: skip if order already exists in database
        if (tianggeOrderRepository.existsByTianggeOrderId(orderId)) {
            log.info("[Tiangge Feed] Order {} already processed. Skipping duplicate seq={}.", orderId, seq);
            feedCursorService.updateCursor(seq);
            return;
        }

        log.info("[Tiangge Feed] Ingesting event seq={}: type={}, orderId={}, product={}, qty={}",
                seq, event.getEventType(), orderId, event.getProductId(), event.getQuantity());

        TianggeOrder order;
        try {
            order = new TianggeOrder(
                    orderId,
                    event.getProductId() != null ? event.getProductId() : "UNKNOWN",
                    event.getQuantity() != null ? event.getQuantity() : 1,
                    TianggeOrderStatus.PENDING
            );
            order = tianggeOrderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Tiangge Feed] Duplicate order constraint hit for orderId {}. Skipping.", orderId);
            feedCursorService.updateCursor(seq);
            return;
        }

        // Evaluate order through decision engine
        decisionEngine.evaluateAndProcessOrder(order);

        // Update cursor to highest processed seq
        feedCursorService.updateCursor(seq);
    }
}
