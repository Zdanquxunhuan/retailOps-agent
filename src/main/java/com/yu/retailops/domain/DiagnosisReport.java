package com.yu.retailops.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiagnosisReport(UUID taskId, Instant startedAt, Instant finishedAt,
                              String orderId, String storeCode, Outcome outcome,
                              String postingStatus, boolean replayExecuted,
                              String summary, List<String> recommendations,
                              List<Evidence> evidence, DiagnosisDetails details) {
    public DiagnosisReport withDetails(DiagnosisDetails value) {
        return new DiagnosisReport(taskId, startedAt, Instant.now(), orderId, storeCode, outcome,
                postingStatus, replayExecuted, summary, recommendations, evidence, value);
    }
    public enum Outcome {
        NEED_INPUT, UNSUPPORTED_SCENARIO, MODEL_UNAVAILABLE,
        ORDER_QUERY_FAILED, OUTSIDE_QUERY_WINDOW, ORDER_UNCONFIRMED,
        INVALID_PLATFORM_RESPONSE, POS_QUERY_FAILED, BOTH_FLOWS_PRESENT,
        PARTIAL_FLOWS_PRESENT, NO_FLOWS_FOUND
    }
    public record Evidence(String id, String tool, Instant observedAt, String status,
                           String detail, Long saleCount, Long payCount) { }
}
