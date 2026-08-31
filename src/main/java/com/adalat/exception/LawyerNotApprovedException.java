package com.adalat.exception;

public class LawyerNotApprovedException extends RuntimeException {
    public LawyerNotApprovedException(String message) {
        super(message);
    }
}
