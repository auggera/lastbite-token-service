package ua.lastbite.token_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ua.lastbite.token_service.config.TokenConfig;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenResponse;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
import ua.lastbite.token_service.exception.InvalidTokenFormatException;
import ua.lastbite.token_service.exception.TokenAlreadyUsedException;
import ua.lastbite.token_service.exception.TokenExpiredException;
import ua.lastbite.token_service.exception.TokenNotFoundException;
import ua.lastbite.token_service.mapper.TokenMapper;
import ua.lastbite.token_service.model.Token;
import ua.lastbite.token_service.repository.TokenRepository;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
public class TokenService {

    private final TokenRepository tokenRepository;
    private final TokenMapper tokenMapper;
    private final TokenConfig tokenConfig;

    @Autowired
    public TokenService(TokenRepository tokenRepository, TokenMapper tokenMapper
            , TokenConfig tokenConfig) {
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
        this.tokenConfig = tokenConfig;
    }

    public TokenResponse generateToken(TokenRequest request) {
        log.info("Generating token for user ID: {}", request.getUserId());

        Token token = tokenMapper.toEntity(request, tokenConfig.getTokenExpirationTime());
        String tokenValue = generateTokenValue(request.getUserId());
        token.setTokenValue(tokenValue);

        log.info("Token successfully generated for user ID: {}", request.getUserId());
        tokenRepository.save(token);
        log.info("Token saved");

        return new TokenResponse(tokenValue);
    }

    private String generateTokenValue(Long userId) {
        return Base64.getEncoder()
                .encodeToString((userId + ":" + UUID.randomUUID()).getBytes());
    }

    public TokenValidationResponse validateToken(String tokenValue) {
        log.info("Validating token: {}", tokenValue);

        if (tokenValue == null || tokenValue.isBlank()) {
            log.error("Validation failed: token is blank or null.");
            throw new InvalidTokenFormatException("Token cannot be null or empty");
        }

        if (!isValidTokenFormat(tokenValue)) {
            log.error("Validation failed: token has invalid format. Token: {}", tokenValue);
            throw new InvalidTokenFormatException("Token format is invalid");
        }

        Token token = tokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new TokenNotFoundException(tokenValue));

        if (isTokenExpired(token)) {
            log.error("Token is expired: {}", token.getTokenValue());
            throw new TokenExpiredException(tokenValue);
        }

        if (token.isUsed()) {
            log.error("Token is used: {}", token.getTokenValue());
            throw new TokenAlreadyUsedException(tokenValue);
        }

        log.info("Token is valid. User ID: {}", token.getUserId());
        markTokenAsUsed(token);

        return new TokenValidationResponse(true, token.getUserId());
    }

    private boolean isTokenExpired(Token token) {
            return token.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void markTokenAsUsed(Token token) {
        log.info("Marking token as used: {}", token.getTokenValue());
        token.setUsed(true);
        tokenRepository.save(token);
    }

    @Scheduled(cron = "0 0 0 * * ?") //Starts every day at midnight
    public void removeExpiredAndUsedTokens() {
        log.info("Starting cleanup of expired and used tokens");
        int deletedTokens = tokenRepository.deleteExpiredOrUsedTokens();
        log.info("Completed cleanup. Number of deleted tokens: {}", deletedTokens);
    }

    private boolean isValidTokenFormat(String token) {
        String regex = "^[A-Za-z0-9+/]+={0,2}$";
        return token.matches(regex);
    }
}
