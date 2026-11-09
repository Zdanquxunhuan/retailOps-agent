package com.yu.retailops.workflow;

import com.yu.retailops.agent.OrderIntentExtractor;
import com.yu.retailops.config.RetailOpsProperties;
import com.yu.retailops.domain.DiagnosisReport;
import com.yu.retailops.domain.DiagnosisReport.Evidence;
import com.yu.retailops.domain.DiagnosisReport.Outcome;
import com.yu.retailops.domain.PlatformOrderResult;
import com.yu.retailops.integration.IntegrationException;
import com.yu.retailops.integration.OrderDetailPort;
import com.yu.retailops.integration.PosFlowPort;
import com.yu.retailops.security.StoreAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public class DiagnosisWorkflow {
    private final OrderIntentExtractor extractor;
    private final OrderDetailPort orders;
    private final PosFlowPort flows;
    private final StoreAccessPolicy access;
    private final DiagnosisStore store;
    private final Semaphore permits;

    public DiagnosisWorkflow(OrderIntentExtractor extractor, OrderDetailPort orders, PosFlowPort flows,
                             StoreAccessPolicy access, DiagnosisStore store, RetailOpsProperties config) {
        this.extractor = extractor;
        this.orders = orders;
        this.flows = flows;
        this.access = access;
        this.store = store;
        this.permits = new Semaphore(config.maxConcurrentDiagnoses());
    }

    public DiagnosisReport diagnose(String operator, String message) {
        if (!permits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "诊断任务繁忙，请稍后重试");
        }
        UUID task = UUID.randomUUID();
        Instant started = Instant.now();
        boolean recorded = false;
        try {
            // Fail before external calls if the local audit record cannot be persisted.
            store.start(task, operator, started);
            recorded = true;
            DiagnosisReport report = execute(task, started, operator, message);
            store.complete(operator, report);
            return report;
        } catch (AccessDeniedException e) {
            if (recorded) store.fail(task, operator, started, "ACCESS_DENIED");
            throw e;
        } catch (RuntimeException e) {
            if (recorded) store.fail(task, operator, started, "INTERNAL_ERROR");
            throw e;
        } finally {
            permits.release();
        }
    }

    public DiagnosisStore.StoredTask get(UUID task, String operator) {
        var saved = store.read(task, operator);
        if (saved == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        if (saved.report() != null && saved.report().storeCode() != null) {
            access.check(operator, saved.report().storeCode()); // Recheck after authorization changes.
        }
        return saved;
    }

    private DiagnosisReport execute(UUID task, Instant start, String operator, String message) {
        List<Evidence> evidence = new ArrayList<>();
        var intent = extractor.extract(message);
        evidence.add(new Evidence("E1", "intent-extraction", Instant.now(), intent.status(),
                intent.explanation(), null, null));
        if (!"READY".equals(intent.status())) {
            Outcome result = switch (intent.status()) {
                case "UNSUPPORTED" -> Outcome.UNSUPPORTED_SCENARIO;
                case "MODEL_UNAVAILABLE" -> Outcome.MODEL_UNAVAILABLE;
                default -> Outcome.NEED_INPUT;
            };
            return report(task, start, null, null, result, intent.explanation(),
                    List.of("补齐或明确单个美团销售订单号后重新提交。"), evidence);
        }

        String orderId = intent.orderId();
        PlatformOrderResult queried = orders.query(orderId);
        if (queried.status() != PlatformOrderResult.Status.FOUND) {
            evidence.add(new Evidence("E2", "meituan-order-detail", Instant.now(), queried.status().name(),
                    queried.explanation(), null, null));
            Outcome result = switch (queried.status()) {
                case OUTSIDE_WINDOW -> Outcome.OUTSIDE_QUERY_WINDOW;
                case UNCONFIRMED -> Outcome.ORDER_UNCONFIRMED;
                case INVALID -> Outcome.INVALID_PLATFORM_RESPONSE;
                default -> Outcome.ORDER_QUERY_FAILED;
            };
            return report(task, start, orderId, null, result, queried.explanation(),
                    List.of("未取得可授权的订单门店信息，未查询思迅；可人工结合订单时间、本地备份和日志核查。",
                            "没有执行补推。"), evidence);
        }

        var order = queried.order();
        // No order facts are persisted or disclosed until the returned store is authorized.
        access.check(operator, order.storeCode());
        evidence.add(new Evidence("E2", "meituan-order-detail", Instant.now(), "FOUND",
                "订单身份校验通过；美团原始状态=" + order.platformStatus()
                        + "；创建时间=" + (order.createdAt() == null ? "未知" : order.createdAt()), null, null));
        try {
            var snapshot = flows.query(orderId, order.storeCode());
            evidence.add(new Evidence("E3", "pos-flow-count", snapshot.observedAt(), "QUERIED",
                    "按配置的数据源、美团门店映射及订单号查询销售/支付流水；计数不证明明细完整或过账完成。",
                    snapshot.saleCount(), snapshot.payCount()));
            List<String> recommendations = new ArrayList<>();
            recommendations.add("两表记录在过账后仍保留，过账状态需要独立核实。");
            Outcome result;
            String summary;
            if (snapshot.saleCount() > 0 && snapshot.payCount() > 0) {
                result = Outcome.BOTH_FLOWS_PRESENT;
                summary = "销售流水和支付流水均已写入思迅，满足当前写入存在性核验条件；过账状态未核实。";
                recommendations.add("不要重复补推；若业务仍有异常，再核对明细、金额及思迅处理状态。");
            } else if (snapshot.saleCount() > 0 || snapshot.payCount() > 0) {
                result = Outcome.PARTIAL_FLOWS_PRESENT;
                summary = "只有一类流水存在，尚未满足正常完成条件，不能认定完整推送成功。";
                recommendations.add("核查是否仍在处理、是否发生异常或存在查询时序差异，不自动补推或补写流水。");
            } else {
                result = Outcome.NO_FLOWS_FOUND;
                summary = "本次查询未发现销售和支付流水；尚不能定位未同步原因，也不代表可以直接补推。";
                recommendations.add("核查日志、本地处理记录、订单业务资格和进行中的任务，再进行补推预检。");
            }
            if (Integer.valueOf(9).equals(order.platformStatus())) {
                recommendations.add("美团返回状态9，按现有回调规则视为已取消；不得据两表为空直接补推。");
            }
            recommendations.add("当前接口仅诊断，未执行BI写入、报文保存或思迅补推。");
            return report(task, start, orderId, order.storeCode(), result, summary, recommendations, evidence);
        } catch (IntegrationException e) {
            evidence.add(new Evidence("E3", "pos-flow-count", Instant.now(), "UNKNOWN", e.getMessage(), null, null));
            return report(task, start, orderId, order.storeCode(), Outcome.POS_QUERY_FAILED, e.getMessage(),
                    List.of("查询失败不等于零条流水，不根据本次失败发起补推。"), evidence);
        }
    }

    private DiagnosisReport report(UUID id, Instant start, String order, String branch, Outcome outcome,
                                   String summary, List<String> recommendations, List<Evidence> evidence) {
        return new DiagnosisReport(id, start, Instant.now(), order, branch, outcome,
                "UNKNOWN", false, summary, List.copyOf(recommendations), List.copyOf(evidence));
    }
}
