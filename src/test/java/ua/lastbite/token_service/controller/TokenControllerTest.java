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
import ua.lastbite.token_service.dto.token.TokenValidationRequest;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
import ua.lastbite.token_service.dto.user.UserDto;
import ua.lastbite.token_service.exception.TokenAlreadyUsedException;
import ua.lastbite.token_service.exception.TokenExpiredException;
import ua.lastbite.token_service.exception.TokenNotFoundException;
import ua.lastbite.token_service.exception.UserNotFoundException;
import ua.lastbite.token_service.model.Token;
import ua.lastbite.token_service.repository.TokenRepository;
import ua.lastbite.token_service.service.TokenService;
import ua.lastbite.token_service.service.UserServiceClient;

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

    @MockBean
    private UserServiceClient userServiceClient;

    @Autowired
    private TokenRepository tokenRepository;

    TokenRequest tokenRequest;
    TokenValidationRequest tokenValidationRequest;
    TokenValidationResponse tokenValidationResponse;
    Token token;

    @BeforeEach
    void setUp() {
        tokenRequest = new TokenRequest(1);
        tokenValidationResponse = new TokenValidationResponse(true, 1);
        tokenValidationRequest = new TokenValidationRequest("tokenValue");

        token = new Token();
        token.setTokenValue("tokenValue");
        token.setUserId(1);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(86400L));
        token.setUsed(false);
    }

    @Test
    void testGenerateTokenSuccessfully() throws Exception {
        Mockito.when(userServiceClient.getUserById(tokenRequest.getUserId()))
                        .thenReturn(new UserDto());

        Mockito.when(tokenService.generateToken(tokenRequest))
                .thenReturn("TokenSample");

        MvcResult result = mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.notNullValue()))
                .andExpect(content().string(Matchers.not("")))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertEquals("TokenSample", responseContent);
    }

    @Test
    void testGenerateTokenUserNotFound() throws Exception {
        Mockito.doThrow(new UserNotFoundException(1))
                        .when(tokenService).generateToken(tokenRequest);

        mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User with ID 1 not found"));
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
