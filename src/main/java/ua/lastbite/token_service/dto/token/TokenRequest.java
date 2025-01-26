package ua.lastbite.token_service.dto.token;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode
public class TokenRequest {

    @NotNull(message = "User ID cannot be empty")
    private Long userId;
}
