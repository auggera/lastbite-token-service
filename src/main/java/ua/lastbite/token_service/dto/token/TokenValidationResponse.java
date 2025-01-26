package ua.lastbite.token_service.dto.token;

import lombok.AllArgsConstructor;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class TokenValidationResponse {

    private boolean valid;
    private Long userId;
}
