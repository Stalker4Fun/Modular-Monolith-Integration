package edu.cit.valendez.tiangge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration component holding Tiangge marketplace connection parameters and instance identification.
 * Package-private to enforce module isolation.
 */
@Component
class TianggeProperties {

    private final String baseUrl;
    private final String clientId;
    private final String instanceId;
    private final String clientInstanceUuid;
    private final String apiKey;i

    public TianggeProperties(
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${tiangge.instance-id:${legacysupply.client-id:21-3360-213}}") String instanceId,
            @Value("${tiangge.api-key:${legacysupply.api-key:}}") String apiKey
    ) {
        this.baseUrl = baseUrl;
        this.clientId = instanceId;
        this.instanceId = instanceId;
        this.clientInstanceUuid = java.util.UUID.nameUUIDFromBytes(("instance-" + instanceId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getClientInstanceUuid() {
        return clientInstanceUuid;
    }

    public String getApiKey() {
        return apiKey;
    }
}
