package ai.jadebase.connector.feishu;

public class FeishuApiException extends RuntimeException {

    private final boolean retryable;

    public FeishuApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public FeishuApiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
}
