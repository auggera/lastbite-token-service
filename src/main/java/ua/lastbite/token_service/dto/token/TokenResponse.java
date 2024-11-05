package ua.lastbite.token_service.dto.token;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class TokenResponse {

    @NotBlank(message = "Token value cannot be empty")
    private String tokenValue;
}