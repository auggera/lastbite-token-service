package ua.lastbite.token_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import ua.lastbite.token_service.config.TokenConfig;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenResponse;
import ua.lastbite.token_service.dto.token.TokenValidationRequest;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
import ua.lastbite.token_service.exception.TokenAlreadyUsedException;
import ua.lastbite.token_service.exception.TokenExpiredException;
import ua.lastbite.token_service.exception.TokenNotFoundException;
import ua.lastbite.token_service.mapper.TokenMapper;
import ua.lastbite.token_service.model.Token;
import ua.lastbite.token_service.repository.TokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
public class TokenServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private TokenConfig tokenConfig;

    @Mock
    private TokenMapper tokenMapper;

    private Token token;
    private TokenRequest tokenRequest;
    private TokenValidationRequest tokenValidationRequest;

    @BeforeEach
    void setUp() {
        token = new Token();
        token.setTokenValue("testToken");
        token.setUserId(1);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(86_400L));
        token.setUsed(false);

        tokenRequest = new TokenRequest(1);
        tokenValidationRequest = new TokenValidationRequest("testToken");
    }

    @Test
    void testGenerateTokenSuccessfully() {
        Mockito.when(tokenMapper.toEntity(tokenRequest, tokenConfig.getTokenExpirationTime()))
                .thenReturn(token);

        Mockito.when(tokenRepository.save(any(Token.class))).thenReturn(token);

        TokenResponse response = tokenService.generateToken(tokenRequest);
        System.out.println(response.getTokenValue());

        assertNotNull(response);
        assertNotNull(response.getTokenValue());
        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now()));

        Mockito.verify(tokenRepository, Mockito.times(1)).save(token);
        Mockito.verify(tokenMapper, Mockito.times(1)).toEntity(tokenRequest, tokenConfig.getTokenExpirationTime());
    }


    @Test
    void testGenerateUniqueTokenForSameUser() {
        Mockito.when(tokenMapper.toEntity(tokenRequest, tokenConfig.getTokenExpirationTime()))
                .thenReturn(token);

        Mockito.when(tokenRepository.save(any(Token.class))).thenReturn(token);

        TokenResponse response1 = tokenService.generateToken(tokenRequest);
        TokenResponse response2 = tokenService.generateToken(tokenRequest);

        assertNotNull(response1);
        assertNotNull(response2);
        assertNotNull(response1.getTokenValue());
        assertNotNull(response2.getTokenValue());
        assertNotEquals(response1, response2);
    }

    @Test
    void testValidateTokenSuccessfully() {
        Mockito.when(tokenRepository.findByTokenValue(tokenValidationRequest.getTokenValue()))
                .thenReturn(Optional.of(token));

        TokenValidationResponse response = tokenService.validateToken(tokenValidationRequest);

        assertTrue(response.isValid());
        assertEquals(1, response.getUserId());
        assertTrue(token.isUsed());

        Mockito.verify(tokenRepository, Mockito.times(1)).save(token);
    }

    @Test
    void testValidateTokenNotFound() {
        Mockito.when(tokenRepository.findByTokenValue(tokenValidationRequest.getTokenValue()))
                .thenReturn(Optional.empty());

        TokenNotFoundException exception = assertThrows(TokenNotFoundException.class, () -> tokenService.validateToken(tokenValidationRequest));

        assertEquals("Token not found: " + tokenValidationRequest.getTokenValue(), exception.getMessage());
        Mockito.verify(tokenRepository, Mockito.never()).save(token);
    }

    @Test
    void testValidateTokenExpired() {
        token.setCreatedAt(LocalDateTime.now().minusSeconds(86_401L));
        token.setExpiresAt(token.getCreatedAt().plusSeconds(86_400L));

        Mockito.when(tokenRepository.findByTokenValue(tokenValidationRequest.getTokenValue()))
                .thenReturn(Optional.of(token));

        TokenExpiredException exception = assertThrows(TokenExpiredException.class, () -> tokenService.validateToken(tokenValidationRequest));

        assertEquals("Token has expired: " + token.getTokenValue(), exception.getMessage());

        Mockito.verify(tokenRepository, Mockito.never()).save(token);
    }

    @Test
    void testValidateTokenIsAlreadyUsed() {
        token.setUsed(true);

        Mockito.when(tokenRepository.findByTokenValue(tokenValidationRequest.getTokenValue()))
                .thenReturn(Optional.of(token));

        TokenAlreadyUsedException exception = assertThrows(TokenAlreadyUsedException.class, () -> tokenService.validateToken(tokenValidationRequest));

        assertEquals("Token has already been used: " + token.getTokenValue(), exception.getMessage());

        Mockito.verify(tokenRepository, Mockito.never()).save(token);
    }
}
