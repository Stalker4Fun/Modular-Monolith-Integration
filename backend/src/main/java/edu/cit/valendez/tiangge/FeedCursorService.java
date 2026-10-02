package edu.cit.valendez.tiangge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing persistent feed reading position in the feed_cursor database table.
 * Package-private to enforce module isolation.
 */
@Service
class FeedCursorService {

    private static final Logger log = LoggerFactory.getLogger(FeedCursorService.class);
    private static final Long DEFAULT_CURSOR_ID = 1L;

    private final FeedCursorRepository feedCursorRepository;

    public FeedCursorService(FeedCursorRepository feedCursorRepository) {
        this.feedCursorRepository = feedCursorRepository;
    }

    /**
     * Gets the current stored last_event_id. Reads from database upon restart.
     * If no cursor record exists, initializes with 0.
     */
    @Transactional
    public long getCurrentCursor() {
        return feedCursorRepository.findById(DEFAULT_CURSOR_ID)
                .map(FeedCursor::getLastEventId)
                .orElseGet(() -> {
                    log.info("[Tiangge Cursor] Initializing new feed cursor row in database with last_event_id = 0");
                    FeedCursor newCursor = new FeedCursor(DEFAULT_CURSOR_ID, 0L);
                    feedCursorRepository.save(newCursor);
                    return 0L;
                });
    }

    /**
     * Updates stored last_event_id in database if newLastEventId is strictly greater.
     */
    @Transactional
    public void updateCursor(long newLastEventId) {
        FeedCursor cursor = feedCursorRepository.findById(DEFAULT_CURSOR_ID)
                .orElseGet(() -> new FeedCursor(DEFAULT_CURSOR_ID, 0L));

        if (newLastEventId > cursor.getLastEventId()) {
            log.info("[Tiangge Cursor] Updating persistent cursor: {} -> {}", cursor.getLastEventId(), newLastEventId);
            cursor.setLastEventId(newLastEventId);
            feedCursorRepository.save(cursor);
        }
    }
}
