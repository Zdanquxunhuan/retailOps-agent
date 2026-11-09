package com.yu.retailops.integration;

import com.yu.retailops.domain.PosFlowSnapshot;

public interface PosFlowPort {
    PosFlowSnapshot query(String orderId, String storeCode);
}
