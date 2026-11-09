package com.yu.retailops.integration;

import com.yu.retailops.domain.PlatformOrderResult;

public interface OrderDetailPort {
    PlatformOrderResult query(String orderId);
}
