package com.yu.retailops.integration.meituan;

import com.yu.retailops.domain.PlatformOrderResult;
import com.yu.retailops.domain.PlatformOrderResult.Order;
import com.yu.retailops.domain.PlatformOrderResult.Status;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class MeituanResponseParser {
    private final JsonMapper json = JsonMapper.builder().build();

    public PlatformOrderResult parse(String expectedOrderId, String body) {
        if (body == null || body.length() > 2_000_000) return invalid();
        try {
            JsonNode root = json.readTree(body);
            if (root == null || !root.isObject()) return invalid();
            JsonNode code = root.path("result_code");
            if (!code.isIntegralNumber()) return invalid();
            if (code.intValue() != 1) {
                JsonNode errors = root.path("error_list");
                if (windowError(root.path("error"))) return window();
                if (errors.isArray()) for (JsonNode error : errors) if (windowError(error)) return window();
                boolean missing = notFound(root.path("error"));
                if (errors.isArray()) for (JsonNode error : errors) missing |= notFound(error);
                if (missing) return new PlatformOrderResult(Status.UNCONFIRMED, null,
                        "美团返回不存在此订单；超过14天的历史订单也可能返回该错误，不能据此确认订单从未存在。");
                return new PlatformOrderResult(Status.FAILED, null,
                        "美团返回业务错误，未继续解析订单或查询思迅。");
            }
            JsonNode data = root.path("data");
            if (!data.isObject()) return invalid();
            JsonNode id = data.path("order_id");
            if (!(id.isIntegralNumber() || id.isString()) || !expectedOrderId.equals(id.asString())) return invalid();
            JsonNode store = data.path("app_poi_code");
            if (!store.isString() || store.asString().isBlank() || store.asString().length() > 128) return invalid();
            JsonNode status = data.path("status");
            if (!status.isIntegralNumber() || !status.canConvertToInt()) return invalid();
            Instant created = null;
            JsonNode ctime = data.path("ctime");
            if (ctime.isIntegralNumber() && ctime.canConvertToLong() && ctime.longValue() > 0) {
                created = Instant.ofEpochSecond(ctime.longValue());
            }
            return new PlatformOrderResult(Status.FOUND,
                    new Order(expectedOrderId, store.asString(), status.intValue(), created),
                    "美团详情查询成功，订单号与请求一致。");
        } catch (RuntimeException e) {
            return invalid(); // Do not put payload snippets or parser exception messages in logs.
        }
    }

    private boolean windowError(JsonNode error) {
        return error.path("msg").asString("").contains("只能查5天内订单");
    }
    private boolean notFound(JsonNode error) {
        return error.path("code").asInt(-1) == 806;
    }
    private PlatformOrderResult window() {
        return new PlatformOrderResult(Status.OUTSIDE_WINDOW, null,
                "订单超出详情接口近5天查询窗口，停止查询重试；可人工核查本地备份和思迅记录。");
    }
    private PlatformOrderResult invalid() {
        return new PlatformOrderResult(Status.INVALID, null, "美团响应结构或订单身份校验失败，未继续处理。");
    }
}
