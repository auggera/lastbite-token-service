package ua.lastbite.token_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ua.lastbite.token_service.config.TokenConfig;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenValidationRequest;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
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
public class TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);

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

    public String generateToken(TokenRequest request) {
        LOGGER.info("Generating token for user ID: {}", request.getUserId());

        Token token = tokenMapper.toEntity(request, tokenConfig.getTokenExpirationTime());
        String tokenValue = generateTokenValue(request.getUserId());
        token.setTokenValue(tokenValue);

        LOGGER.info("Token successfully generated for user ID: {}", request.getUserId());
        tokenRepository.save(token);
        LOGGER.info("Token saved");
        return tokenValue;
    }

    private String generateTokenValue(Integer userId) {
        return Base64.getEncoder()
                .encodeToString((userId + ":" + UUID.randomUUID()).getBytes());
    }

    public TokenValidationResponse validateToken(TokenValidationRequest request) {
        LOGGER.info("Validating token: {}", request.getTokenValue());

        Token token = tokenRepository.findByTokenValue(request.getTokenValue())
                .orElseThrow(() -> new TokenNotFoundException(request.getTokenValue()));

        if (isTokenExpired(token)) {
            LOGGER.error("Token is expired: {}", token.getTokenValue());
            throw new TokenExpiredException(request.getTokenValue());
        }

        if (token.isUsed()) {
            LOGGER.error("Token is used: {}", token.getTokenValue());
            throw new TokenAlreadyUsedException(request.getTokenValue());
        }

        LOGGER.info("Token is valid. User ID: {}", token.getUserId());
        markTokenAsUsed(token);

        return new TokenValidationResponse(true, token.getUserId());
    }

    private boolean isTokenExpired(Token token) {
            return token.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void markTokenAsUsed(Token token) {
        LOGGER.info("Marking token as used: {}", token.getTokenValue());
        token.setUsed(true);
        tokenRepository.save(token);
    }

    @Scheduled(cron = "0 0 0 * * ?") //Starts every day at midnight
    public void removeExpiredAndUsedTokens() {
        LOGGER.info("Starting cleanup of expired and used tokens");
        int deletedTokens = tokenRepository.deleteExpiredOrUsedTokens();
        LOGGER.info("Completed cleanup. Number of deleted tokens: {}", deletedTokens);
    }
}
