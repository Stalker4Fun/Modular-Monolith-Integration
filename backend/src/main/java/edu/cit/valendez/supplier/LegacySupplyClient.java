package edu.cit.valendez.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Handles HTTP requests to LegacySupply API endpoints with XML payload formatting,
 * timeout controls (<=3s), session token management, and error handling.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final LegacySupplySessionManager sessionManager;
    private final HttpClient httpClient;
    private final XmlMapper xmlMapper;

    public LegacySupplyClient(LegacySupplySessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.xmlMapper = new XmlMapper();
    }

    public XmlPurchaseOrderAck placeOrder(String supplierSku, int qty, String buyerRef, String requestId) throws LegacySupplyException {
        XmlPurchaseOrder poReq = new XmlPurchaseOrder(supplierSku, qty, buyerRef);
        try {
            String xmlBody = xmlMapper.writeValueAsString(poReq);
            return executeWithSession((sessionToken) -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sessionManager.getBaseUrl() + "/purchase-orders"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/xml")
                        .header("Accept", "application/xml")
                        .header("X-LS-Session", sessionToken)
                        .header("X-Request-Id", requestId)
                        .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                        .build();

                log.info("[LegacySupply] Placing order: SKU={}, Qty={}, BuyerRef={}, RequestId={}", supplierSku, qty, buyerRef, requestId);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 201 || response.statusCode() == 200) {
                    return xmlMapper.readValue(response.body(), XmlPurchaseOrderAck.class);
                }

                throw parseError(response);
            });
        } catch (LegacySupplyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LegacySupply] Order placement HTTP request failed: {}", e.getMessage());
            throw new LegacySupplyException("E-SYS-99", "HTTP request failed: " + e.getMessage(), e);
        }
    }

    public XmlPurchaseOrderStatus getOrderStatus(String poNumber) throws LegacySupplyException {
        try {
            return executeWithSession((sessionToken) -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sessionManager.getBaseUrl() + "/purchase-orders/" + poNumber))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/xml")
                        .header("X-LS-Session", sessionToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return xmlMapper.readValue(response.body(), XmlPurchaseOrderStatus.class);
                }
                throw parseError(response);
            });
        } catch (LegacySupplyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LegacySupply] Get order status HTTP request failed: {}", e.getMessage());
            throw new LegacySupplyException("E-SYS-99", "HTTP request failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface SessionAction<T> {
        T execute(String sessionToken) throws Exception;
    }

    private <T> T executeWithSession(SessionAction<T> action) throws LegacySupplyException {
        String token = sessionManager.getValidSessionToken();
        try {
            return action.execute(token);
        } catch (LegacySupplyException e) {
            // If authentication failure occurs (401 / E-AUTH-*), invalidate session and retry once
            if (isAuthError(e.getErrorCode())) {
                log.warn("[LegacySupply] Auth error received ({}), invalidating token and retrying...", e.getErrorCode());
                sessionManager.invalidateSession();
                String newToken = sessionManager.getValidSessionToken();
                try {
                    return action.execute(newToken);
                } catch (Exception ex) {
                    if (ex instanceof LegacySupplyException) throw (LegacySupplyException) ex;
                    throw new LegacySupplyException("E-SYS-99", ex.getMessage(), ex);
                }
            }
            throw e;
        } catch (Exception e) {
            throw new LegacySupplyException("E-SYS-99", e.getMessage(), e);
        }
    }

    private boolean isAuthError(String code) {
        return code != null && (code.startsWith("E-AUTH-") || code.equals("401"));
    }

    private LegacySupplyException parseError(HttpResponse<String> response) {
        String body = response.body();
        if (body != null && body.contains("<LSError>")) {
            try {
                XmlLSError lsErr = xmlMapper.readValue(body, XmlLSError.class);
                return new LegacySupplyException(lsErr.getCode(), lsErr.getMessage());
            } catch (Exception ignored) {}
        }
        return new LegacySupplyException("HTTP-" + response.statusCode(), "Server returned HTTP " + response.statusCode());
    }
}

