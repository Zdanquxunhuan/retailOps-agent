package com.yu.retailops.integration;

/** Only safe, predefined messages cross the integration boundary. */
public class IntegrationException extends RuntimeException {
    public IntegrationException(String message) { super(message); }
}
