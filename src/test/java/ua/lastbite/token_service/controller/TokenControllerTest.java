package ua.lastbite.token_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenResponse;
import ua.lastbite.token_service.dto.token.TokenValidationRequest;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
import ua.lastbite.token_service.exception.TokenAlreadyUsedException;
import ua.lastbite.token_service.exception.TokenExpiredException;
import ua.lastbite.token_service.exception.TokenNotFoundException;
import ua.lastbite.token_service.model.Token;
import ua.lastbite.token_service.service.TokenService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest
public class TokenControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    private TokenService tokenService;

    private static final String TOKEN_VALUE = "tokenValue";
    private TokenRequest tokenRequest;
    private TokenValidationRequest tokenValidationRequest;
    private TokenValidationResponse tokenValidationResponse;
    private TokenResponse tokenResponse;

    @BeforeEach
    void setUp() {
        tokenRequest = new TokenRequest(1);
        tokenValidationResponse = new TokenValidationResponse(true, 1);
        tokenValidationRequest = new TokenValidationRequest(TOKEN_VALUE);
        tokenResponse = new TokenResponse(TOKEN_VALUE);

        Token token = new Token();
        token.setTokenValue(TOKEN_VALUE);
        token.setUserId(1);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(86400L));
        token.setUsed(false);
    }

    @Test
    void testGenerateTokenSuccessfully() throws Exception {

        Mockito.when(tokenService.generateToken(tokenRequest))
                .thenReturn(tokenResponse);

        MvcResult result = mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.notNullValue()))
                .andExpect(content().string(Matchers.not("")))
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains(TOKEN_VALUE));
    }

    @Test
    void testValidateTokenSuccessfully() throws Exception {
        Mockito.when(tokenService.validateToken(tokenValidationRequest))
                .thenReturn(tokenValidationResponse);

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void testValidateTokenNotFound() throws Exception {
        Mockito.when(tokenService.validateToken(tokenValidationRequest))
                .thenThrow(new TokenNotFoundException(tokenValidationRequest.getTokenValue()));

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Token not found: " + tokenValidationRequest.getTokenValue()));
    }

    @Test
    void testValidateTokenIsExpired() throws Exception {
        Mockito.when(tokenService.validateToken(tokenValidationRequest))
                .thenThrow(new TokenExpiredException(tokenValidationRequest.getTokenValue()));

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isGone())
                .andExpect(content().string("Token has expired: " + tokenValidationRequest.getTokenValue()));
    }

    @Test
    void testValidateTokenIsAlreadyUsed() throws Exception {
        Mockito.when(tokenService.validateToken(tokenValidationRequest))
                .thenThrow(new TokenAlreadyUsedException(tokenValidationRequest.getTokenValue()));

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Token has already been used: " + tokenValidationRequest.getTokenValue()));
    }
}
