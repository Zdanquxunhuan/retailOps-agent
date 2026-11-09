package com.yu.retailops.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Safe, persisted execution view. This is an investigation journal, not model chain-of-thought. */
public record AgentDiagnosisRun(UUID taskId, String state, String stopReason, LocalDate orderDate,
        DiagnosisReport report, RemoteLogEvidence remoteLogs, List<ToolStep> steps, RepairPlan repairPlan) {
    public record ToolStep(int number, String tool, String decisionSource, String status,
            Instant startedAt, long durationMillis, List<String> evidenceIds) { }
    public record RepairPlan(String status, boolean executable, List<String> blockers,
            List<String> nextChecks, List<String> evidenceIds) { }
}
