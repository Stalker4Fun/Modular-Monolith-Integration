package edu.cit.valendez.tiangge;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Data transfer object for an individual feed event from Tiangge Marketplace.
 * The API returns:
 *   - seq: integer (used as cursor for pagination via ?after=N)
 *   - eventId: opaque string like "evt_abc123"
 *   - type: "ORDER_PLACED" | "ORDER_CANCELLED"
 * Package-private to enforce module isolation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class TianggeFeedEventDto {

    /** Integer sequence number — used as the feed cursor (after=N param). */
    @JsonProperty("seq")
    private Long seq;

    /** Opaque event identifier string, e.g. "evt_ce1d16b09b9df15b". */
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("type")
    @JsonAlias({"event_type", "type", "eventType"})
    private String eventType;

    @JsonProperty("orderId")
    @JsonAlias({"order_id", "tiangge_order_id", "orderId"})
    private String orderId;

    @JsonProperty("productId")
    @JsonAlias({"product_id", "productId"})
    private String productId;

    @JsonProperty("quantity")
    @JsonAlias({"qty", "quantity"})
    private Integer quantity;

    @JsonProperty("lines")
    private List<FeedLineDto> lines;

    @JsonProperty("status")
    @JsonAlias({"order_status", "status"})
    private String status;

    @JsonProperty("placedAt")
    @JsonAlias({"placedAt", "timestamp", "created_at"})
    private String timestamp;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeedLineDto {
        @JsonProperty("sellerSku")
        @JsonAlias({"sellerSku", "product_id", "productId", "sku"})
        private String sellerSku;

        @JsonProperty("qty")
        @JsonAlias({"qty", "quantity"})
        private Integer qty;

        public FeedLineDto() {
        }

        public FeedLineDto(String sellerSku, Integer qty) {
            this.sellerSku = sellerSku;
            this.qty = qty;
        }

        public String getSellerSku() {
            return sellerSku;
        }

        public void setSellerSku(String sellerSku) {
            this.sellerSku = sellerSku;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }
    }

    public TianggeFeedEventDto() {
    }

    public TianggeFeedEventDto(Long seq, String eventType, String orderId, String productId, Integer quantity) {
        this.seq = seq;
        this.eventId = "evt_" + seq;
        this.eventType = eventType;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        if (productId != null && !productId.isBlank()) {
            return productId;
        }
        if (lines != null && !lines.isEmpty() && lines.get(0).getSellerSku() != null) {
            return lines.get(0).getSellerSku();
        }
        return "UNKNOWN";
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        if (quantity != null && quantity > 0) {
            return quantity;
        }
        if (lines != null && !lines.isEmpty() && lines.get(0).getQty() != null) {
            return lines.get(0).getQty();
        }
        return 1;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public List<FeedLineDto> getLines() {
        return lines;
    }

    public void setLines(List<FeedLineDto> lines) {
        this.lines = lines;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
