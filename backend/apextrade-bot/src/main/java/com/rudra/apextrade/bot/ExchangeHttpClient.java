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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ExchangeHttpClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public static class AuthSession {
        private final AtomicLong userIdRef;
        private final String email;
        private final String password;
        private final AtomicReference<String> tokenRef;

        public AuthSession(long userId, String email, String password, String initialToken) {
            this.userIdRef = new AtomicLong(userId);
            this.email = email;
            this.password = password;
            this.tokenRef = new AtomicReference<>(initialToken);
        }

        public long userId() { return userIdRef.get(); }
        public String email() { return email; }
        public String password() { return password; }
        public String token() { return tokenRef.get(); }
        public void update(String newToken, long newUserId) {
            tokenRef.set(newToken);
            userIdRef.set(newUserId);
        }
    }

    public ExchangeHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.mapper = new ObjectMapper();
    }

    public AuthSession authenticate(String email, String password) throws Exception {
        String token = fetchToken(email, password);
        long userId = parseUserId(token);
        return new AuthSession(userId, email, password, token);
    }

    public synchronized String refreshToken(AuthSession session) {
        try {
            System.out.printf("[Auth Refresh] Re-authenticating session for %s (Trader #%d)...%n",
                    session.email(), session.userId());
            String newToken = fetchToken(session.email(), session.password());
            long newUserId = parseUserId(newToken);
            session.update(newToken, newUserId);
            System.out.printf("[Auth Refresh] Successfully renewed 24h JWT for %s (Trader #%d).%n", session.email(), session.userId());
            return newToken;
        } catch (Exception e) {
            System.err.printf("[Auth Refresh Failed] Unable to renew JWT for %s: %s%n",
                    session.email(), e.getMessage());
            return session.token();
        }
    }

    private String fetchToken(String email, String password) throws Exception {
        try {
            String regBody = mapper.writeValueAsString(Map.of("email", email, "password", password));
            HttpRequest regReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(regBody))
                    .build();
            httpClient.send(regReq, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}

        String loginBody = mapper.writeValueAsString(Map.of("email", email, "password", password));
        HttpRequest logReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();

        HttpResponse<String> logRes = httpClient.send(logReq, HttpResponse.BodyHandlers.ofString());
        if (logRes.statusCode() != 200) {
            throw new IllegalStateException("Authentication failed for " + email + " (HTTP " + logRes.statusCode() + "): " + logRes.body());
        }

        JsonNode node = mapper.readTree(logRes.body());
        return node.get("token").asText();
    }

    private long parseUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = mapper.readTree(decoded);
                if (payload.has("userId")) {
                    return payload.get("userId").asLong();
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    public long placeLimitOrder(AuthSession session, Side side, double price, double quantity) throws Exception {
        Map<String, Object> payload = Map.of(
                "side", side.name(),
                "orderType", OrderType.LIMIT.name(),
                "price", String.format(java.util.Locale.US, "%.2f", price),
                "quantity", String.format(java.util.Locale.US, "%.8f", quantity)
        );

        String json = mapper.writeValueAsString(payload);
        HttpResponse<String> res = sendOrderRequest(session.token(), json);

        // Transparent auto-renewal if 24h JWT expired
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            String newToken = refreshToken(session);
            res = sendOrderRequest(newToken, json);
        }

        if (res.statusCode() == 200 || res.statusCode() == 202) {
            JsonNode node = mapper.readTree(res.body());
            return node.has("orderId") ? node.get("orderId").asLong() : 0L;
        }

        return 0L;
    }

    private HttpResponse<String> sendOrderRequest(String token, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/orders"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public boolean cancelOrder(AuthSession session, long orderId) {
        if (orderId <= 0) return false;
        try {
            HttpResponse<String> res = sendCancelRequest(session.token(), orderId);

            // Transparent auto-renewal if 24h JWT expired
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                String newToken = refreshToken(session);
                res = sendCancelRequest(newToken, orderId);
            }

            return res.statusCode() == 200 || res.statusCode() == 202;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse<String> sendCancelRequest(String token, long orderId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/orders/" + orderId))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .DELETE()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
