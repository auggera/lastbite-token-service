package ua.lastbite.token_service.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ua.lastbite.token_service.dto.token.TokenRequest;
import ua.lastbite.token_service.model.Token;

import static org.junit.jupiter.api.Assertions.*;

class TokenMapperTest {

    private final TokenMapper tokenMapper = Mappers.getMapper(TokenMapper.class);

    @Test
    void testTokenMapper() {
        TokenRequest tokenRequest = new TokenRequest(1L);

        Token token = tokenMapper.toEntity(tokenRequest, 86_400L);

        assertNotNull(token);
        assertEquals(1, token.getUserId());
        assertNotNull(token.getCreatedAt());
        assertNotNull(token.getExpiresAt());
        assertFalse(token.isUsed());
        assertTrue(token.getExpiresAt().isAfter(token.getCreatedAt()));
    }
}
