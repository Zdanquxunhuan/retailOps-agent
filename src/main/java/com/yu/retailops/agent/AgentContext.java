package com.yu.retailops.agent;

import com.yu.retailops.domain.*;
import com.yu.retailops.domain.AgentDiagnosisRun.ToolStep;
import com.yu.retailops.domain.DiagnosisDetails.KnowledgeSearch;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Per-request state. Identity is bound by Java; never populated from a model tool argument. */
public final class AgentContext {
    final UUID taskId = UUID.randomUUID();
    final Instant startedAt = Instant.now();
    final String operator;
    final LocalDate orderDate;
    final long deadline;
    final List<DiagnosisReport.Evidence> evidence = new ArrayList<>();
    final List<ToolStep> steps = new ArrayList<>();
    final Set<String> attempted = new LinkedHashSet<>();
    String orderId;
    PlatformOrderResult platform;
    PlatformOrderResult.Order authorizedOrder;
    PosFlowSnapshot flows;
    boolean flowFailed;
    RemoteLogEvidence logs;
    KnowledgeSearch knowledge = new KnowledgeSearch("NOT_RUN", null, List.of());
    String state = "RUNNING", stopReason = "IN_PROGRESS";
    DiagnosisReport.Outcome intakeOutcome = DiagnosisReport.Outcome.NEED_INPUT;
    String intakeMessage = "请提供单个美团销售订单号和订单日期YYYY-MM-DD。";

    public AgentContext(String operator, LocalDate orderDate, int seconds) {
        this.operator = operator;
        this.orderDate = orderDate;
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        logs = RemoteLogEvidence.empty("NOT_RUN", orderDate, "尚未调用远程日志工具。");
    }
    boolean expired() { return System.nanoTime() >= deadline; }
    void stop(String state, String reason) { this.state = state; this.stopReason = reason; }
    String storeCode() { return authorizedOrder == null ? null : authorizedOrder.storeCode(); }
}
