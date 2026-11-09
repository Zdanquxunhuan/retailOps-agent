package com.yu.retailops.security;

import com.yu.retailops.config.RetailOpsProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class StoreAccessPolicy {
    private final RetailOpsProperties properties;
    public StoreAccessPolicy(RetailOpsProperties properties) { this.properties = properties; }

    public void check(String operator, String store) {
        var user = properties.users().get(operator);
        if (user == null || !user.stores().contains(store)) {
            throw new AccessDeniedException("订单不在当前用户的门店权限范围内");
        }
    }
}
