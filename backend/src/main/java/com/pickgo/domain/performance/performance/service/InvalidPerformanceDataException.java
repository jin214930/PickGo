package com.pickgo.domain.performance.performance.service;

public class InvalidPerformanceDataException extends RuntimeException {
    public InvalidPerformanceDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
