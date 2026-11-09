package com.yu.retailops.domain;

import java.time.Instant;

/** Minimal permitted fields only: never carry customer data, signatures or raw SDK responses. */
public record PlatformOrderResult(Status status, Order order, String explanation) {
    public enum Status { FOUND, OUTSIDE_WINDOW, UNCONFIRMED, FAILED, INVALID }
    public record Order(String orderId, String storeCode, Integer platformStatus, Instant createdAt) { }
}
