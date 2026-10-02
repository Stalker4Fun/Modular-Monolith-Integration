package edu.cit.valendez.tiangge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Client for interacting with the external Tiangge Marketplace API.
 * Ensures outgoing HTTP requests carry live Client and Instance ID headers.
 * Package-private to enforce module isolation.
 */
@Component
class TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final TianggeProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Instant startTime;

    public TianggeClient(TianggeProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
        this.startTime = Instant.now();
    }

    private HttpRequest.Builder createRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Id", properties.getClientId())
                .header("X-Client-Instance", properties.getClientInstanceUuid())
                .header("X-Instance-ID", properties.getInstanceId())
                .header("X-App-Instance-ID", properties.getInstanceId());

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("X-API-Key", properties.getApiKey());
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }

        return builder;
    }

    /**
     * Sends periodic heartbeat to Tiangge API.
     */
    public boolean sendHeartbeat() {
        String url = properties.getBaseUrl() + "/instances/heartbeat";
        long uptime = Duration.between(startTime, Instant.now()).getSeconds();
        String jsonPayload = String.format(
                "{\"appName\":\"modular-monolith\",\"startedAt\":\"%s\",\"uptimeSeconds\":%d}",
                startTime.toString(), uptime
        );
        return executeHttpRequest(url, "POST", jsonPayload, "Heartbeat");
    }

    /**
     * Publishes product listings to Tiangge API.
     */
    public boolean publishListings() {
        String url = properties.getBaseUrl() + "/listings";
        String jsonPayload = "[" +
                "{\"sellerSku\":\"P100\",\"title\":\"Wireless Mouse\",\"supplierSku\":\"ZTY-3082\"}," +
                "{\"sellerSku\":\"P200\",\"title\":\"Mechanical Keyboard\",\"supplierSku\":\"ZTY-8985\"}," +
                "{\"sellerSku\":\"P300\",\"title\":\"USB-C Hub\",\"supplierSku\":\"ZTY-1364\"}" +
                "]";
        return executeHttpRequest(url, "PUT", jsonPayload, "Publish Listings");
    }

    /**
     * Publishes stock availability for all products.
     */
    public boolean publishBulkStock(String jsonStockArray) {
        String url = properties.getBaseUrl() + "/stock";
        return executeHttpRequest(url, "PUT", jsonStockArray, "Bulk Stock Update");
    }

    /**
     * Polls the order feed endpoint for events occurring after sinceEventId.
     */
    public List<TianggeFeedEventDto> fetchFeed(long sinceEventId) {
        String url = String.format("%s/feed?after=%d&limit=50",
                properties.getBaseUrl(), sinceEventId);

        try {
            HttpRequest request = createRequestBuilder(url).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body().trim();
                if (body.isEmpty()) {
                    return Collections.emptyList();
                }

                if (body.startsWith("[")) {
                    return objectMapper.readValue(body, new TypeReference<List<TianggeFeedEventDto>>() {});
                } else if (body.startsWith("{")) {
                    TianggeFeedResponseDto res = objectMapper.readValue(body, TianggeFeedResponseDto.class);
                    return res.getEvents() != null ? res.getEvents() : Collections.emptyList();
                }
            } else {
                log.warn("[Tiangge HTTP] Feed polling returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[Tiangge HTTP] Failed to fetch order feed: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Posts order decision (ACCEPTED, REJECTED, BACKORDERED) to Tiangge API.
     */
    public boolean sendDecision(String tianggeOrderId, String status, String reason) {
        String url = String.format("%s/orders/%s/decision", properties.getBaseUrl(), tianggeOrderId);
        String jsonPayload = String.format(
                "{\"decision\":\"%s\",\"shopOrderId\":\"O-%s\",\"status\":\"%s\",\"reason\":\"%s\",\"instance_id\":\"%s\"}",
                status, tianggeOrderId, status, reason != null ? reason.replace("\"", "\\\"") : "", properties.getInstanceId()
        );

        return executeHttpRequest(url, "POST", jsonPayload, "Order Decision");
    }

    /**
     * Resolves a backordered order upon replenishment delivery.
     */
    public boolean resolveBackorder(String tianggeOrderId, String status) {
        String url = String.format("%s/orders/%s/resolution", properties.getBaseUrl(), tianggeOrderId);
        String jsonPayload = String.format("{\"status\":\"%s\"}", status);
        return executeHttpRequest(url, "POST", jsonPayload, "Backorder Resolution");
    }

    /**
     * Pushes stock updates to Tiangge Marketplace API.
     */
    public boolean sendStockUpdate(String productId, int stock) {
        String jsonPayload = String.format("[{\"sellerSku\":\"%s\",\"available\":%d}]", productId, stock);
        return publishBulkStock(jsonPayload);
    }

    /**
     * Confirms order cancellation to Tiangge API within decision window.
     */
    public boolean sendCancellationConfirmation(String tianggeOrderId, String reason) {
        String url = String.format("%s/orders/%s/cancellation", properties.getBaseUrl(), tianggeOrderId);
        String jsonPayload = "{\"restocked\":true}";

        boolean success = executeHttpRequest(url, "POST", jsonPayload, "Cancellation Confirmation");
        if (!success) {
            String fallbackUrl = String.format("%s/orders/%s/cancellation-confirm", properties.getBaseUrl(), tianggeOrderId);
            String fallbackPayload = String.format(
                    "{\"status\":\"CANCELLED\",\"reason\":\"%s\",\"instance_id\":\"%s\"}",
                    reason != null ? reason.replace("\"", "\\\"") : "Order cancelled", properties.getInstanceId()
            );
            return executeHttpRequest(fallbackUrl, "POST", fallbackPayload, "Fallback Cancellation Confirm");
        }
        return true;
    }

    private boolean executeHttpRequest(String url, String method, String jsonBody, String actionLabel) {
        try {
            HttpRequest.Builder builder = createRequestBuilder(url);
            if ("POST".equalsIgnoreCase(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            } else if ("PUT".equalsIgnoreCase(method)) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
            } else if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[Tiangge HTTP] {} sent successfully: HTTP {}", actionLabel, response.statusCode());
                return true;
            } else {
                log.warn("[Tiangge HTTP] {} failed for {}: HTTP {} {}", actionLabel, url, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[Tiangge HTTP] {} HTTP error for {}: {}", actionLabel, url, e.getMessage());
        }
        return false;
    }
}
