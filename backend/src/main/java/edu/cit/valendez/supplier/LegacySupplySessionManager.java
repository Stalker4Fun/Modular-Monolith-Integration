package edu.cit.valendez.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Handles LegacySupply session authentication and token lifecycle.
 */
@Component
class LegacySupplySessionManager {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySessionManager.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final HttpClient httpClient;
    private final XmlMapper xmlMapper;

    private String currentSessionToken;

    public LegacySupplySessionManager(
            @Value("${legacysupply.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${legacysupply.client-id:${LS_CLIENT_ID:21-3360-213}}") String clientId,
            @Value("${legacysupply.api-key:${LS_API_KEY:}}") String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = resolveApiKey(apiKey);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.xmlMapper = new XmlMapper();
    }

    private static String resolveApiKey(String configuredKey) {
        if (configuredKey != null && !configuredKey.trim().isEmpty()) {
            return configuredKey.trim();
        }
        String sysEnv = System.getenv("LS_API_KEY");
        if (sysEnv != null && !sysEnv.trim().isEmpty()) {
            return sysEnv.trim();
        }
        String sysProp = System.getProperty("LS_API_KEY");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        return "";
    }

    public synchronized String getValidSessionToken() throws LegacySupplyException {
        if (currentSessionToken != null && !currentSessionToken.isEmpty()) {
            return currentSessionToken;
        }
        return authenticate();
    }

    public synchronized void invalidateSession() {
        log.info("[LegacySupply] Invalidation of current session token requested.");
        this.currentSessionToken = null;
    }

    public synchronized String authenticate() throws LegacySupplyException {
        String keyToUse = resolveApiKey(this.apiKey);
        if (keyToUse.isEmpty()) {
            throw new LegacySupplyException("E-AUTH-01", "LS_API_KEY environment variable is not configured");
        }

        try {
            XmlAuthRequest authReq = new XmlAuthRequest(clientId, keyToUse);
            String xmlBody = xmlMapper.writeValueAsString(authReq);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth/token"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                    .build();

            log.info("[LegacySupply] Requesting new auth session for ClientId: {}", clientId);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                XmlAuthResponse authResp = xmlMapper.readValue(response.body(), XmlAuthResponse.class);
                if (authResp != null && authResp.getSessionToken() != null) {
                    this.currentSessionToken = authResp.getSessionToken();
                    log.info("[LegacySupply] Session token acquired successfully.");
                    return this.currentSessionToken;
                }
            }

            String errCode = "E-AUTH-01";
            String errMsg = "Failed to obtain session token (HTTP " + response.statusCode() + ")";
            if (response.body() != null && response.body().contains("<LSError>")) {
                try {
                    XmlLSError lsErr = xmlMapper.readValue(response.body(), XmlLSError.class);
                    errCode = lsErr.getCode();
                    errMsg = lsErr.getMessage();
                } catch (Exception ignored) {}
            }
            throw new LegacySupplyException(errCode, errMsg);
        } catch (LegacySupplyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LegacySupply] Authentication request failed: {}", e.getMessage());
            throw new LegacySupplyException("E-SYS-99", "Authentication failed: " + e.getMessage(), e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getClientId() {
        return clientId;
    }
}

