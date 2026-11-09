package com.yu.retailops.workflow;

import com.yu.retailops.config.RetailOpsProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.Semaphore;

/** Shared by V2 and V3 so separate endpoints cannot bypass the concurrency bound. */
@Component
public class DiagnosisCapacity {
    private final Semaphore permits;
    public DiagnosisCapacity(RetailOpsProperties properties) {
        permits = new Semaphore(properties.maxConcurrentDiagnoses());
    }
    public void acquire() {
        if (!permits.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "诊断任务繁忙，请稍后重试");
    }
    public void release() { permits.release(); }
}
