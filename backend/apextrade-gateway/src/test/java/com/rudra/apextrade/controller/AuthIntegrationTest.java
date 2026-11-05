package com.rudra.apextrade.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rudra.apextrade.dto.LoginRequest;
import com.rudra.apextrade.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("End-to-End: Register (No Token) -> Login (Mint Token) -> Verify Authenticated Profile")
    void testRegisterLoginAndProfileFlow() throws Exception {
        String email = "trader_alice_" + System.currentTimeMillis() + "@apextrade.io";
        String password = "StrongPassword123!";

        // 1. Register new trader (Must NOT issue a token)
        RegisterRequest registerReq = new RegisterRequest(email, password);
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        assertEquals(email, registerJson.get("email").asText());
        long userId = registerJson.get("userId").asLong();
        assertTrue(userId > 0);
        assertNull(registerJson.get("token"), "Register must not issue a session token");

        // 2. Duplicate registration rejected with HTTP 400 Bad Request
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"));

        // 3. Login with correct credentials (Mints session token)
        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String loginToken = loginJson.get("token").asText();
        assertNotNull(loginToken);
        assertEquals(userId, loginJson.get("userId").asLong());
        assertEquals(email, loginJson.get("email").asText());

        // 4. Login with incorrect password rejected with HTTP 401
        LoginRequest badLoginReq = new LoginRequest(email, "wrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLoginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        // 5. Access protected /api/users/me endpoint with Bearer token
        MvcResult meResult = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode meJson = objectMapper.readTree(meResult.getResponse().getContentAsString());
        assertEquals(userId, meJson.get("userId").asLong());
        assertEquals(email, meJson.get("email").asText());

        // 6. Access protected /api/users/me endpoint without token rejected with HTTP 401 Unauthorized
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
