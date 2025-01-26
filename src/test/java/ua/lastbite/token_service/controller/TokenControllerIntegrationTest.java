package ua.lastbite.token_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenResponse;
import ua.lastbite.token_service.dto.user.UserDto;
import ua.lastbite.token_service.dto.user.UserRole;
import ua.lastbite.token_service.exception.TokenNotFoundException;
import ua.lastbite.token_service.model.Token;
import ua.lastbite.token_service.repository.TokenRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest
class TokenControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TOKEN_VALUE = "validToken123";
    private TokenRequest tokenRequest;
    private Token existingToken;

    @BeforeEach
    void setUpRequest() {
        tokenRequest = new TokenRequest(1L);

        UserDto userDto = new UserDto();
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

        MvcResult result = mockMvc.perform(post("/api/tokens/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenValue").exists())
                .andReturn();

        String newTokenValue = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class).getTokenValue();

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

    @BeforeEach
    void setUpExistingToken() {
        existingToken = new Token();
        existingToken.setUserId(1L);
        existingToken.setTokenValue(TOKEN_VALUE);
        existingToken.setUsed(false);
        existingToken.setCreatedAt(LocalDateTime.now());
        existingToken.setExpiresAt(LocalDateTime.now().plusSeconds(86400L));
    }

    @Test
    void testValidateTokenSuccessfully() throws Exception {
        tokenRepository.save(existingToken);

        mockMvc.perform(post("/api/tokens/validate/{token}", TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void testValidateTokenNotFound() throws Exception {
        mockMvc.perform(post("/api/tokens/validate/{token}", TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Token not found: " + TOKEN_VALUE));
    }

    @Test
    void testValidateTokenIAlreadyUsed() throws Exception {
        existingToken.setUsed(true);
        tokenRepository.save(existingToken);

        mockMvc.perform(post("/api/tokens/validate/{token}", TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().string("Token has already been used: " + TOKEN_VALUE));
    }

    @Test
    void testValidateTokenIsExpired() throws Exception {
        existingToken.setExpiresAt(LocalDateTime.now().minusSeconds(5));
        tokenRepository.save(existingToken);

        mockMvc.perform(post("/api/tokens/validate/{token}", TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(content().string("Token has expired: " + TOKEN_VALUE));
    }
}
