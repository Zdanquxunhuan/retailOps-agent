package com.yu.retailops.agent;

import com.yu.retailops.domain.*;
import com.yu.retailops.domain.AgentDiagnosisRun.RepairPlan;
import com.yu.retailops.domain.DiagnosisDetails.*;
import com.yu.retailops.domain.DiagnosisReport.Outcome;
import com.yu.retailops.knowledge.LocalKnowledgeRetriever;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Facts and repair blockers are derived from observations; LLM text cannot overwrite them. */
@Component
public class AgentReportAssembler {
    private final LocalKnowledgeRetriever knowledge;
    public AgentReportAssembler(LocalKnowledgeRetriever knowledge) { this.knowledge = knowledge; }

    public DiagnosisReport base(AgentContext c) {
        Outcome outcome = c.intakeOutcome;
        String summary = c.intakeMessage;
        if (c.platform != null && c.authorizedOrder == null) {
            outcome = switch (c.platform.status()) {
                case OUTSIDE_WINDOW -> Outcome.OUTSIDE_QUERY_WINDOW;
                case UNCONFIRMED -> Outcome.ORDER_UNCONFIRMED;
                case INVALID -> Outcome.INVALID_PLATFORM_RESPONSE;
                default -> Outcome.ORDER_QUERY_FAILED;
            };
            summary = c.platform.explanation();
        } else if (c.authorizedOrder != null) {
            outcome = Outcome.INVESTIGATION_INCOMPLETE;
            summary = "订单身份与门店权限已核验；尚未取得思迅查询结果。";
            if (c.flowFailed) {
                outcome = Outcome.POS_QUERY_FAILED;
                summary = "思迅查询失败，流水状态未知，不能视为零条记录。";
            } else if (c.flows != null) {
                if (c.flows.saleCount() > 0 && c.flows.payCount() > 0) {
                    outcome = Outcome.BOTH_FLOWS_PRESENT;
                    summary = "两类流水均存在，满足写入存在性核验；不证明明细、金额正确或已经过账。";
                } else if (c.flows.saleCount() > 0 || c.flows.payCount() > 0) {
                    outcome = Outcome.PARTIAL_FLOWS_PRESENT;
                    summary = "仅一类流水存在，需核对处理进度、查询时序及事务记录；不能认定完整成功。";
                } else {
                    outcome = Outcome.NO_FLOWS_FOUND;
                    summary = "本次查询未发现两类流水，原因及补推资格尚未确认。";
                }
            }
        }
        List<String> checks = new ArrayList<>();
        if (outcome == Outcome.PARTIAL_FLOWS_PRESENT) checks.add("核查进行中任务、事务和两表状态，不整单盲目重推。");
        if (outcome == Outcome.NO_FLOWS_FOUND) checks.add("核实数据源、回调及处理日志、订单业务资格和进行中任务。");
        if (c.authorizedOrder != null && Integer.valueOf(9).equals(c.authorizedOrder.platformStatus()))
            checks.add("平台状态9，按现有业务规则视为已取消；禁止依据空流水补推。");
        if ("RECEIPT_MARKER_FOUND".equals(c.logs.callbackReceipt()))
            checks.add("发现订单及门店匹配的回调接收标记；只证明日志记录该事件，不证明后续写入成功。");
        else checks.add("尚无足够日志证据确认回调接收，不能断言美团漏推。");
        c.knowledge.hits().forEach(h -> checks.add(h.nextCheck()));
        checks.add("本版本仅生成诊断和处理计划，未执行补推；过账状态保持UNKNOWN。");
        return new DiagnosisReport(c.taskId, c.startedAt, Instant.now(), c.orderId, c.storeCode(), outcome,
                "UNKNOWN", false, summary, List.copyOf(checks), List.copyOf(c.evidence), null);
    }

    public AgentDiagnosisRun snapshot(AgentContext c) {
        DiagnosisReport report = base(c);
        if (c.storeCode() != null) {
            validate(c.operator, c.storeCode(), c.knowledge.hits(), c.evidence, c.logs.search());
            report = report.withDetails(new DiagnosisDetails("3", c.logs.search(), c.knowledge,
                    new Explanation("RULE_RENDERED", "模型选择调查步骤，事实和阻断条件由Java生成；SOP为条件检索。",
                            c.knowledge.hits())));
        }
        List<String> blockers = new ArrayList<>(List.of("REPLAY_NOT_IMPLEMENTED", "POSTING_STATUS_UNKNOWN"));
        if (c.storeCode() == null) blockers.add("ORDER_IDENTITY_NOT_AUTHORIZED");
        if (c.flows == null) blockers.add("POS_STATUS_UNKNOWN");
        else if (c.flows.saleCount() > 0 || c.flows.payCount() > 0) blockers.add("EXISTING_FLOW_REQUIRES_RECONCILIATION");
        else blockers.add("EMPTY_FLOWS_DO_NOT_AUTHORIZE_REPLAY");
        if (c.authorizedOrder != null && Integer.valueOf(9).equals(c.authorizedOrder.platformStatus()))
            blockers.add("ORDER_CANCELLED");
        Set<String> ids = evidenceIds(c.evidence, c.logs.search());
        return new AgentDiagnosisRun(c.taskId, c.state, c.stopReason, c.orderDate, report, c.logs,
                List.copyOf(c.steps), new RepairPlan("ADVISORY_ONLY", false, List.copyOf(blockers),
                        report.recommendations(), List.copyOf(ids)));
    }

    public void validateStored(String operator, AgentDiagnosisRun run) {
        if (run.report().storeCode() == null || run.report().details() == null) return;
        validate(operator, run.report().storeCode(), run.report().details().knowledge().hits(),
                run.report().evidence(), run.remoteLogs().search());
    }
    private void validate(String operator, String store, List<KnowledgeHit> hits,
                          List<DiagnosisReport.Evidence> facts, LogSearch logs) {
        knowledge.checkCitations(operator, store, hits);
        Set<String> allowed = evidenceIds(facts, logs);
        for (KnowledgeHit hit : hits) if (!allowed.containsAll(hit.evidenceIds()))
            throw new AccessDeniedException("引用不属于本次诊断证据");
    }
    private Set<String> evidenceIds(List<DiagnosisReport.Evidence> facts, LogSearch logs) {
        Set<String> ids = new LinkedHashSet<>();
        facts.forEach(e -> ids.add(e.id())); logs.matches().forEach(e -> ids.add(e.id())); return ids;
    }
}
