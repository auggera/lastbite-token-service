package ua.lastbite.token_service.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenResponse;
import ua.lastbite.token_service.dto.token.TokenValidationRequest;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
import ua.lastbite.token_service.service.TokenService;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;

    @Autowired
    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenController.class);

    @PostMapping("/generate")
    public ResponseEntity<TokenResponse> generateToken(@Valid @RequestBody TokenRequest request) {
        LOGGER.info("Received request to generate token for user ID: {}", request.getUserId());
        TokenResponse tokenResponse = tokenService.generateToken(request);
        LOGGER.info("Token successfully generated for user ID: {}", request.getUserId());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        LOGGER.info("Received request to validate token: {}", request.getTokenValue());
        TokenValidationResponse response = tokenService.validateToken(request);
        LOGGER.info("Token validation result for token {}: {}", request.getTokenValue(), response.isValid());
        return ResponseEntity.ok(response);
    }
}
