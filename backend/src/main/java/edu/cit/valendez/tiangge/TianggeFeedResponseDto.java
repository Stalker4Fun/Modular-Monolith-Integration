package edu.cit.valendez.tiangge;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object wrapping feed polling responses from Tiangge Marketplace.
 * Package-private to enforce module isolation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class TianggeFeedResponseDto {

    @JsonProperty("events")
    @JsonAlias({"data", "events", "items"})
    private List<TianggeFeedEventDto> events = new ArrayList<>();

    @JsonProperty("nextCursor")
    @JsonAlias({"nextCursor", "last_event_id", "cursor", "lastEventId"})
    private Long lastEventId;

    public TianggeFeedResponseDto() {
    }

    public TianggeFeedResponseDto(List<TianggeFeedEventDto> events, Long lastEventId) {
        this.events = events;
        this.lastEventId = lastEventId;
    }

    public List<TianggeFeedEventDto> getEvents() {
        return events;
    }

    public void setEvents(List<TianggeFeedEventDto> events) {
        this.events = events;
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(Long lastEventId) {
        this.lastEventId = lastEventId;
    }
}
