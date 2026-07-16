package ai.jadebase.identity.domain;

public class IdentityConflictException extends RuntimeException {
    public IdentityConflictException(String message) {
        super(message);
    }
}
