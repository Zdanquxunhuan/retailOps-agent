package com.yu.retailops.agent;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** Dotted business names map to provider-compatible function names. All arguments are server-bound. */
public enum DiagnosisTool {
    ORDER("order.detail.query", "order_detail_query", "通过美团SDK查询当前订单详情，用于身份和门店核验。"),
    POS("pos.flow.query", "pos_flow_query", "查询当前已授权订单的思迅销售与支付流水计数。"),
    LOGS("logs.search", "logs_search", "读取当前已授权订单的日期前后一天gz日志，查找接收及异常线索。"),
    KNOWLEDGE("knowledge.search", "knowledge_search", "结合当前流水状态和日志标记检索适用SOP及下一步排查建议。");

    final String businessName, functionName, description;
    DiagnosisTool(String businessName, String functionName, String description) {
        this.businessName = businessName; this.functionName = functionName; this.description = description;
    }
    ToolCallback definitionOnly() {
        ToolDefinition definition = ToolDefinition.builder().name(functionName).description(description)
                .inputSchema("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}").build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String input) {
                throw new IllegalStateException("工具必须经Agent执行器校验，不允许框架直接调用");
            }
        };
    }
}
