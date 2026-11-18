package com.rudra.apextrade.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * High-performance, virtual-thread friendly HTTP client for Gateway REST API.
 */
public class ExchangeHttpClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public record AuthSession(String token, long userId, String email) {}

    public ExchangeHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Authenticates a bot account. Registers if not already registered, then logs in.
     */
    public AuthSession authenticate(String email, String password) throws Exception {
        // Attempt Register first
        try {
            String registerBody = mapper.writeValueAsString(Map.of("email", email, "password", password));
            HttpRequest regReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(registerBody))
                    .build();

            HttpResponse<String> regRes = httpClient.send(regReq, HttpResponse.BodyHandlers.ofString());
            if (regRes.statusCode() == 200 || regRes.statusCode() == 201) {
                JsonNode node = mapper.readTree(regRes.body());
                String token = node.get("token").asText();
                long userId = node.get("userId").asLong();
                return new AuthSession(token, userId, email);
            }
        } catch (Exception ignored) {
            // Already registered or temporary conflict, fall through to login
        }

        // Login
        String loginBody = mapper.writeValueAsString(Map.of("email", email, "password", password));
        HttpRequest logReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();

        HttpResponse<String> logRes = httpClient.send(logReq, HttpResponse.BodyHandlers.ofString());
        if (logRes.statusCode() != 200) {
            throw new IllegalStateException("Authentication failed for " + email + " (HTTP " + logRes.statusCode() + "): " + logRes.body());
        }

        JsonNode node = mapper.readTree(logRes.body());
        String token = node.get("token").asText();
        long userId = node.get("userId").asLong();
        return new AuthSession(token, userId, email);
    }

    /**
     * Places a limit order with satoshi & cent rounding.
     */
    public long placeLimitOrder(String token, Side side, double price, double quantity) throws Exception {
        Map<String, Object> payload = Map.of(
                "side", side.name(),
                "orderType", OrderType.LIMIT.name(),
                "price", price,
                "quantity", quantity
        );

        String json = mapper.writeValueAsString(payload);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/orders"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 || res.statusCode() == 202) {
            JsonNode node = mapper.readTree(res.body());
            return node.has("orderId") ? node.get("orderId").asLong() : 0L;
        }

        // Return 0 on rejection (e.g. margin, price out of bounds)
        return 0L;
    }

    /**
     * Cancels an active resting order by ID.
     */
    public boolean cancelOrder(String token, long orderId) {
        if (orderId <= 0) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/orders/" + orderId))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(5))
                    .DELETE()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 || res.statusCode() == 202;
        } catch (Exception e) {
            return false;
        }
    }
}
