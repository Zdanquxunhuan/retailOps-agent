package com.yu.retailops.agent;

import com.yu.retailops.config.DiagnosisV3Properties;
import com.yu.retailops.domain.*;
import com.yu.retailops.domain.AgentDiagnosisRun.ToolStep;
import com.yu.retailops.domain.DiagnosisReport.*;
import com.yu.retailops.integration.*;
import com.yu.retailops.integration.logs.RemoteGzipLogReader;
import com.yu.retailops.knowledge.LocalKnowledgeRetriever;
import com.yu.retailops.security.StoreAccessPolicy;
import com.yu.retailops.workflow.DiagnosisCapacity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Synchronous, bounded, read-only investigation with per-step durable checkpoints. */
@Service
public class AgentDiagnosisService {
    private final DiagnosisV3Properties config;
    private final OrderIntentExtractor extractor;
    private final OrderDetailPort orders;
    private final PosFlowPort flows;
    private final RemoteGzipLogReader logs;
    private final LocalKnowledgeRetriever knowledge;
    private final StoreAccessPolicy access;
    private final AgentRunStore store;
    private final AgentReportAssembler reports;
    private final DeepSeekDiagnosisPlanner planner;
    private final DiagnosisCapacity capacity;

    public AgentDiagnosisService(DiagnosisV3Properties config, OrderIntentExtractor extractor,
            OrderDetailPort orders, PosFlowPort flows, RemoteGzipLogReader logs,
            LocalKnowledgeRetriever knowledge, StoreAccessPolicy access, AgentRunStore store,
            AgentReportAssembler reports, DeepSeekDiagnosisPlanner planner, DiagnosisCapacity capacity) {
        this.config = config; this.extractor = extractor; this.orders = orders; this.flows = flows;
        this.logs = logs; this.knowledge = knowledge; this.access = access; this.store = store;
        this.reports = reports; this.planner = planner; this.capacity = capacity;
    }

    public AgentDiagnosisRun diagnose(String operator, String message, LocalDate date) {
        if (!config.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Version3诊断入口未启用");
        capacity.acquire();
        AgentContext c = new AgentContext(operator, date, config.timeoutSeconds());
        try {
            checkpoint(c); // Persistence must succeed before ANY external request.
            if (date == null || date.getYear() < 2000 || date.isAfter(LocalDate.now(ZoneId.of(config.remoteLogs().zoneId())))) {
                c.stop("NEED_INPUT", "ORDER_DATE_REQUIRED"); return checkpoint(c);
            }
            var intent = extractor.extract(message);
            c.evidence.add(new Evidence("E1", "intent-extraction", Instant.now(), intent.status(), intent.explanation(), null, null));
            if (!"READY".equals(intent.status())) {
                c.intakeOutcome = switch (intent.status()) {
                    case "UNSUPPORTED" -> Outcome.UNSUPPORTED_SCENARIO;
                    case "MODEL_UNAVAILABLE" -> Outcome.MODEL_UNAVAILABLE;
                    default -> Outcome.NEED_INPUT;
                };
                c.intakeMessage = intent.explanation();
                c.stop("STOPPED", intent.status()); return checkpoint(c);
            }
            c.orderId = intent.orderId();
            if (c.expired()) { c.stop("STOPPED", "TIME_BUDGET"); return checkpoint(c); }
            execute(c, DiagnosisTool.ORDER, "JAVA_IDENTITY_GATE");
            if (c.authorizedOrder == null) {
                c.stop("STOPPED", "ORDER_IDENTITY_UNAVAILABLE"); return checkpoint(c);
            }
            var history = planner.start(c);
            boolean fallback = !config.plannerEnabled();
            Set<String> callIds = new HashSet<>();
            while (true) {
                if (c.expired()) { c.stop("STOPPED", "TIME_BUDGET"); break; }
                List<DiagnosisTool> allowed = available(c);
                if (allowed.isEmpty()) { c.stop("COMPLETED", fallback ? "RULE_FALLBACK_COMPLETE" : "INVESTIGATION_COMPLETE"); break; }
                if (c.steps.size() >= config.maxSteps()) { c.stop("STOPPED", "STEP_BUDGET"); break; }
                DiagnosisTool next;
                DeepSeekDiagnosisPlanner.Decision decision = null;
                if (fallback) next = allowed.get(0);
                else {
                    Instant start = Instant.now();
                    decision = planner.next(history, allowed);
                    if (c.expired()) { c.stop("STOPPED", "TIME_BUDGET"); break; }
                    if (decision.tool() == null || !callIds.add(decision.callId())) {
                        c.steps.add(new ToolStep(c.steps.size() + 1, "planner", "GUARD",
                                decision.tool() == null ? decision.status() : "DUPLICATE_CALL_ID",
                                start, Duration.between(start, Instant.now()).toMillis(), List.of()));
                        checkpoint(c); fallback = true; continue;
                    }
                    next = decision.tool();
                }
                execute(c, next, fallback ? "RULE_FALLBACK" : "DEEPSEEK_TOOL_CALL");
                if (!fallback) planner.observe(history, decision, c);
            }
            return checkpoint(c);
        } catch (AccessDeniedException e) {
            // Do not persist a newly unauthorized store's platform/log data.
            c.authorizedOrder = null; c.platform = null; c.flows = null;
            c.logs = RemoteLogEvidence.empty("NOT_RUN", date, "访问被拒绝。");
            c.knowledge = new DiagnosisDetails.KnowledgeSearch("NOT_RUN", null, List.of());
            c.evidence.clear(); c.steps.clear();
            c.intakeMessage = "无权访问该订单或门店。"; c.stop("FAILED", "ACCESS_DENIED");
            checkpoint(c); throw e;
        } catch (RuntimeException e) {
            c.stop("FAILED", "INTERNAL_ERROR");
            try { checkpoint(c); } catch (RuntimeException ignored) { }
            throw e;
        } finally { capacity.release(); }
    }

    public AgentDiagnosisRun get(UUID id, String operator) {
        var run = store.read(id, operator);
        if (run == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        if (run.report().storeCode() != null) access.check(operator, run.report().storeCode());
        reports.validateStored(operator, run);
        return run;
    }

    private List<DiagnosisTool> available(AgentContext c) {
        List<DiagnosisTool> list = new ArrayList<>();
        if (!c.attempted.contains(DiagnosisTool.POS.businessName)) list.add(DiagnosisTool.POS);
        if (!c.attempted.contains(DiagnosisTool.LOGS.businessName)) list.add(DiagnosisTool.LOGS);
        if (list.isEmpty() && !c.attempted.contains(DiagnosisTool.KNOWLEDGE.businessName)) list.add(DiagnosisTool.KNOWLEDGE);
        return list;
    }

    private void execute(AgentContext c, DiagnosisTool tool, String source) {
        if (tool != DiagnosisTool.ORDER) {
            access.check(c.operator, c.storeCode());
            if (!available(c).contains(tool)) throw new IllegalStateException("工具前置条件未满足");
        }
        if (!c.attempted.add(tool.businessName)) throw new IllegalStateException("重复工具调用被拒绝");
        Instant start = Instant.now();
        int index = c.steps.size();
        c.steps.add(new ToolStep(index + 1, tool.businessName, source, "STARTED", start, 0, List.of()));
        checkpoint(c);
        String status;
        List<String> ids;
        switch (tool) {
            case ORDER -> {
                var result = orders.query(c.orderId);
                if (result.status() == PlatformOrderResult.Status.FOUND) {
                    if (result.order() == null || !c.orderId.equals(result.order().orderId()))
                        throw new IllegalStateException("平台身份不一致");
                    access.check(c.operator, result.order().storeCode());
                    c.authorizedOrder = result.order();
                }
                c.platform = result;
                c.evidence.add(new Evidence("E2", tool.businessName, Instant.now(), result.status().name(),
                        result.status() == PlatformOrderResult.Status.FOUND ? "美团订单身份及门店权限核验通过。" : result.explanation(), null, null));
                status = result.status().name(); ids = List.of("E2");
            }
            case POS -> {
                try {
                    c.flows = flows.query(c.orderId, c.storeCode());
                    c.evidence.add(new Evidence("E3", tool.businessName, c.flows.observedAt(), "QUERIED",
                            "销售及支付流水计数，不能证明过账或明细正确。", c.flows.saleCount(), c.flows.payCount()));
                    status = "QUERIED";
                } catch (IntegrationException e) {
                    c.flowFailed = true;
                    c.evidence.add(new Evidence("E3", tool.businessName, Instant.now(), "UNKNOWN",
                            "思迅查询失败，未将失败转换为零条记录。", null, null));
                    status = "UNKNOWN";
                }
                ids = List.of("E3");
            }
            case LOGS -> {
                c.logs = logs.search(c.operator, c.orderId, c.storeCode(), c.orderDate);
                status = c.logs.search().status(); ids = c.logs.search().matches().stream().map(m -> m.id()).toList();
            }
            case KNOWLEDGE -> {
                c.knowledge = knowledge.retrieve(c.operator, reports.base(c), c.logs.search());
                knowledge.checkCitations(c.operator, c.storeCode(), c.knowledge.hits());
                status = c.knowledge.status(); ids = c.knowledge.hits().stream().map(h -> h.id()).toList();
            }
            default -> throw new IllegalStateException("未注册工具");
        }
        if (c.storeCode() != null) access.check(c.operator, c.storeCode());
        c.steps.set(index, new ToolStep(index + 1, tool.businessName, source, status, start,
                Duration.between(start, Instant.now()).toMillis(), ids));
        checkpoint(c);
    }
    private AgentDiagnosisRun checkpoint(AgentContext c) {
        var run = reports.snapshot(c); store.save(c.operator, run); return run;
    }
}
