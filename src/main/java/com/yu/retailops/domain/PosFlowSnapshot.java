package com.yu.retailops.domain;

import java.time.Instant;

public record PosFlowSnapshot(long saleCount, long payCount, Instant observedAt) {
    public PosFlowSnapshot {
        if (saleCount < 0 || payCount < 0) throw new IllegalArgumentException("Negative row count");
    }
}
