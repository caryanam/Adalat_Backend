package com.adalat.service.ai;

public class AiProviderException extends RuntimeException {
    public enum ErrorCategory {
        AUTH_ERROR,
        QUOTA_EXCEEDED,
        PROVIDER_OVERLOADED,
        NETWORK_ERROR,
        TIMEOUT,
        INVALID_PROVIDER_RESPONSE,
        JSON_PARSE_ERROR,
        UNKNOWN_PROVIDER_ERROR
    }

    private final ErrorCategory category;

    public AiProviderException(String message) {
        super(message);
        this.category = ErrorCategory.UNKNOWN_PROVIDER_ERROR;
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
        this.category = ErrorCategory.UNKNOWN_PROVIDER_ERROR;
    }

    public AiProviderException(ErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public AiProviderException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ErrorCategory getCategory() {
        return category;
    }
}
