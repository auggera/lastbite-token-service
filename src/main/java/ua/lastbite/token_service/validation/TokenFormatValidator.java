package ua.lastbite.token_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TokenFormatValidator implements ConstraintValidator<ValidTokenFormat, String> {

    private static final String TOKEN_REGEX = "^[A-Za-z0-9+/]+={0,2}$";

    @Override
    public boolean isValid(String tokenValue, ConstraintValidatorContext context) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            return true; // Let @NotBlank handle the case
        }

        return tokenValue.matches(TOKEN_REGEX);
    }
}
