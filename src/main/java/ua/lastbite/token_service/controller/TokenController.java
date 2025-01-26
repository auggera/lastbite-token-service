package ua.lastbite.token_service.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.dto.token.TokenResponse;
import ua.lastbite.token_service.dto.token.TokenValidationResponse;
import ua.lastbite.token_service.service.TokenService;

@RestController
@RequestMapping("/api/tokens")
@Slf4j
public class TokenController {

    private final TokenService tokenService;

    @Autowired
    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/generate")
    public ResponseEntity<TokenResponse> generateToken(@Valid @RequestBody TokenRequest request) {
        log.info("Received request to generate token for user ID: {}", request.getUserId());
        TokenResponse tokenResponse = tokenService.generateToken(request);
        log.info("Token successfully generated for user ID: {}", request.getUserId());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/validate/{token}")
    public ResponseEntity<TokenValidationResponse> validateToken(@PathVariable("token") String token) {
        log.info("Received request to validate token: {}", token);
        TokenValidationResponse response = tokenService.validateToken(token);
        log.info("Token validation result for token {}: {}", token, response.isValid());
        return ResponseEntity.ok(response);
    }
}
