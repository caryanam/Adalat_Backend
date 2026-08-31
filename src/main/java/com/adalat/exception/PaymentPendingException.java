package com.adalat.exception;

public class PaymentPendingException extends RuntimeException {
    public PaymentPendingException(String message) {
        super(message);
    }
}
