package ua.lastbite.token_service.exception;

public class InvalidTokenFormatException extends RuntimeException {
    public InvalidTokenFormatException(String message) {
        super(message);
    }
}
