package ua.lastbite.token_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenValidationRequest;
import ua.lastbite.token_service.dto.user.UserDto;
import ua.lastbite.token_service.dto.user.UserRole;
import ua.lastbite.token_service.exception.ServiceUnavailableException;
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
public class TokenControllerIntegrationTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(TokenControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserServiceClient userServiceClient;

    private TokenRequest tokenRequest;
    private TokenValidationRequest tokenValidationRequest;
    private Token existingToken;
    private UserDto userDto;

    @BeforeEach
    void setUpRequest() {
        tokenRequest = new TokenRequest(1);

        tokenValidationRequest = new TokenValidationRequest("validToken123");

        userDto = new UserDto();
        userDto.setId(1);
        userDto.setFirstName("John");
        userDto.setLastName("Doe");
        userDto.setEmail("john@example.com");
        userDto.setRole(UserRole.CUSTOMER);
    }

    @BeforeEach
    public void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE token RESTART IDENTITY");
    }

    @Test
    void testGenerateTokenSuccessfully() throws Exception {
        Mockito.when(userServiceClient.getUserById(tokenRequest.getUserId()))
                .thenReturn(userDto);

        MvcResult result = mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String newTokenValue = result.getResponse().getContentAsString();

        Token savedToken = tokenRepository.findByTokenValue(newTokenValue)
                .orElseThrow(() -> new TokenNotFoundException(newTokenValue));

        assertEquals(tokenRequest.getUserId(), savedToken.getUserId());
        assertEquals(newTokenValue, savedToken.getTokenValue());
        assertFalse(savedToken.isUsed());
        assertNotNull(savedToken.getCreatedAt());
        assertNotNull(savedToken.getExpiresAt());
        assertTrue(savedToken.getExpiresAt().isAfter(savedToken.getCreatedAt()));
    }

    @Test
    void testGenerateTokenUserNotFound() throws Exception {
        Mockito.when(userServiceClient.getUserById(tokenRequest.getUserId()))
                .thenThrow(new UserNotFoundException(tokenRequest.getUserId()));

        mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User with ID " + tokenRequest.getUserId() + " not found"));
    }

    @Test
    void testGenerateTokenUserIdIsNull() throws Exception {
        tokenRequest.setUserId(null);

        mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.userId").value("User ID cannot be empty"));
    }

    @Test
    void testGenerateTokenRequestIsNull() throws Exception {
        tokenRequest = null;

        mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Request body is missing or invalid"));
    }

    @Test
    void testGenerateTokenServiceUnavailable() throws Exception {
        Mockito.when(userServiceClient.getUserById(tokenRequest.getUserId()))
                .thenThrow(new ServiceUnavailableException("Failed to communicate with user-service"));

        mockMvc.perform(post("/api/tokens/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Failed to communicate with user-service"));
    }

    @BeforeEach
    void setUpExistingToken() {
        existingToken = new Token();
        existingToken.setUserId(1);
        existingToken.setTokenValue("validToken123");
        existingToken.setUsed(false);
        existingToken.setCreatedAt(LocalDateTime.now());
        existingToken.setExpiresAt(LocalDateTime.now().plusSeconds(86400L));
    }

    @Test
    void testValidateTokenSuccessfully() throws Exception {
        tokenRepository.save(existingToken);

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void testValidateTokenNotFound() throws Exception {
        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Token not found: " + tokenValidationRequest.getTokenValue()));
    }

    @Test
    void testValidateTokenIAlreadyUsed() throws Exception {
        existingToken.setUsed(true);
        tokenRepository.save(existingToken);

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Token has already been used: " + tokenValidationRequest.getTokenValue()));
    }

    @Test
    void testValidateTokenIsExpired() throws Exception {
        existingToken.setExpiresAt(LocalDateTime.now().minusSeconds(5));
        tokenRepository.save(existingToken);

        mockMvc.perform(post("/api/tokens/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isGone())
                .andExpect(content().string("Token has expired: " + tokenValidationRequest.getTokenValue()));
    }

    @Test
    void testValidateTokenValueIsNull() throws Exception {
        tokenValidationRequest.setTokenValue(null);

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tokenValue").value("Token cannot be empty"));
    }

    @Test
    void testValidateTokenRequestIsNull() throws Exception {
        tokenValidationRequest = null;

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Request body is missing or invalid"));
    }

    @Test
    void testValidateTokenIsTooShort() throws Exception {
        tokenValidationRequest.setTokenValue("short");

        mockMvc.perform(post("/api/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tokenValue").value("Token length must be between 10 and 100 characters"));
    }

    @Test
    void testValidateTokenIsTooLong() throws Exception {
        tokenValidationRequest.setTokenValue("long".repeat(26));

        mockMvc.perform(post("/api/tokens/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tokenValue").value("Token length must be between 10 and 100 characters"));
    }

    @Test
    void testValidateTokenInvalidFormat() throws Exception {
        tokenValidationRequest.setTokenValue("abc123!@#asdasdad");

        mockMvc.perform(post("/api/tokens/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenValidationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tokenValue").value("Invalid token format"));
    }
}
