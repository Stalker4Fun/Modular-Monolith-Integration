package edu.cit.valendez.tiangge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Tracks the last processed feed event position for Tiangge integration.
 * Package-private to maintain module encapsulation.
 */
@Entity
@Table(name = "feed_cursor")
class FeedCursor {

    @Id
    private Long id = 1L;

    @Column(name = "last_event_id", nullable = false)
    private Long lastEventId = 0L;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public FeedCursor() {
        this.updatedAt = OffsetDateTime.now();
    }

    public FeedCursor(Long id, Long lastEventId) {
        this.id = id;
        this.lastEventId = lastEventId;
        this.updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(Long lastEventId) {
        this.lastEventId = lastEventId;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
