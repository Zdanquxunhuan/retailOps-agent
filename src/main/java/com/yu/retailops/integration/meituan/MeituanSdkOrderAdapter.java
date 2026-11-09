package com.yu.retailops.integration.meituan;

import com.yu.retailops.config.RetailOpsProperties;
import com.yu.retailops.domain.PlatformOrderResult;
import com.yu.retailops.integration.OrderDetailPort;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Loads the vendor's bundled JAR without exposing its legacy libraries/service providers
 * to Boot 4. Only JDK strings cross this boundary; no SDK classes enter the domain model.
 * This is dependency isolation, NOT a security sandbox for untrusted JAR files.
 */
@Component
public class MeituanSdkOrderAdapter implements OrderDetailPort {
    private final RetailOpsProperties.Meituan config;
    private final MeituanResponseParser parser;
    private final URLClassLoader loader;
    private final Constructor<?> systemConstructor;
    private final Constructor<?> requestConstructor;
    private final Method setOrderId;
    private final Method doRequest;
    private final Method getResult;

    public MeituanSdkOrderAdapter(RetailOpsProperties properties, MeituanResponseParser parser) {
        this.config = properties.meituan();
        this.parser = parser;
        URLClassLoader candidate = null;
        try {
            if (!Files.isRegularFile(config.sdkJar())) throw new IOException("SDK JAR missing");
            var environment = config.sdkConfigDirectory().resolve("environment.properties");
            Properties sdkSettings = new Properties();
            try (var input = Files.newInputStream(environment)) { sdkSettings.load(input); }
            // The supplied business integration uses appId/appSecret + orderId.
            // Do not accidentally enable SDK OAuth token refresh/write-back behavior.
            if (!"1".equals(sdkSettings.getProperty("isSig"))) {
                throw new IOException("SDK signature mode must be explicitly configured");
            }
            String baseUrl = sdkSettings.getProperty("url", "").trim();
            if (!"1".equals(sdkSettings.getProperty("env"))
                    || (!baseUrl.isEmpty() && !"https://waimaiopen.meituan.com".equals(baseUrl))) {
                throw new IOException("Only the known Meituan HTTPS endpoint is allowed");
            }
            for (String key : new String[]{"socketTimeOut", "connectTimeOut", "connectRequestTimeOut"}) {
                int timeout = Integer.parseInt(sdkSettings.getProperty(key, "0"));
                if (timeout < 1 || timeout > 15000) throw new IOException("Unbounded SDK timeout");
            }
            candidate = new URLClassLoader(new URL[]{
                    config.sdkConfigDirectory().toUri().toURL(), config.sdkJar().toUri().toURL()
            }, ClassLoader.getPlatformClassLoader());
            // Inspect types without initializing the SDK or making any request at startup.
            Class<?> systemType = Class.forName("com.sankuai.meituan.shangou.open.sdk.domain.SystemParam", false, candidate);
            Class<?> requestType = Class.forName("com.sankuai.meituan.shangou.open.sdk.request.OrderGetOrderDetailRequest", false, candidate);
            Class<?> responseType = Class.forName("com.sankuai.meituan.shangou.open.sdk.response.SgOpenResponse", false, candidate);
            systemConstructor = systemType.getConstructor(String.class, String.class);
            requestConstructor = requestType.getConstructor(systemType);
            setOrderId = requestType.getMethod("setOrder_id", String.class);
            doRequest = requestType.getMethod("doRequest");
            getResult = responseType.getMethod("getRequestResult");
            loader = candidate;
        } catch (Exception | LinkageError e) {
            if (candidate != null) try { candidate.close(); } catch (IOException ignored) { }
            // Configuration/SDK exceptions can contain URLs or secrets: do not include their text.
            throw new IllegalStateException("美团SDK加载失败，请检查JAR、配置目录、签名模式及超时配置");
        }
    }

    @Override
    public PlatformOrderResult query(String orderId) {
        if (orderId == null || !orderId.matches("[0-9]{10,32}")) {
            throw new IllegalArgumentException("订单号格式不正确");
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            Object params = systemConstructor.newInstance(config.appId(), config.appSecret());
            Object request = requestConstructor.newInstance(params);
            setOrderId.invoke(request, orderId);
            Object response = doRequest.invoke(request);
            if (response == null) return failed();
            String body = (String) getResult.invoke(response);
            return parser.parse(orderId, body);
        } catch (Exception | LinkageError e) {
            return failed(); // Never log SDK responses, signatures or underlying exception messages.
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private PlatformOrderResult failed() {
        return new PlatformOrderResult(PlatformOrderResult.Status.FAILED, null,
                "美团SDK查询失败，可能为网络、鉴权或SDK异常；未将其视为订单不存在，未自动重试。");
    }

    @PreDestroy
    public void close() throws IOException { loader.close(); }
}
