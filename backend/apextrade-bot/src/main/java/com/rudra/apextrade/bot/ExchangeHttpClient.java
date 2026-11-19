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

    public AuthSession authenticate(String email, String password) throws Exception {
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

    public long placeLimitOrder(String token, Side side, double price, double quantity) throws Exception {
        Map<String, Object> payload = Map.of(
                "side", side.name(),
                "orderType", OrderType.LIMIT.name(),
                "price", String.format(java.util.Locale.US, "%.2f", price),
                "quantity", String.format(java.util.Locale.US, "%.8f", quantity)
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

        return 0L;
    }

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
