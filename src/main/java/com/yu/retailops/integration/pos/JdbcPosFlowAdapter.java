package com.yu.retailops.integration.pos;

import com.yu.retailops.config.RetailOpsProperties;
import com.yu.retailops.domain.PosFlowSnapshot;
import com.yu.retailops.integration.IntegrationException;
import com.yu.retailops.integration.PosFlowPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JdbcPosFlowAdapter implements PosFlowPort {
    private final RetailOpsProperties properties;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public JdbcPosFlowAdapter(RetailOpsProperties properties) { this.properties = properties; }

    @Override
    public PosFlowSnapshot query(String orderId, String storeCode) {
        var route = properties.posRoutes().get(storeCode);
        if (route == null) throw new IntegrationException("该门店未配置思迅只读数据源映射，不能判断流水状态。");
        HikariDataSource pool = pools.computeIfAbsent(storeCode, ignored -> createPool(route));
        String schema = "[" + route.schema() + "]";
        // Table/column names are fixed; schema is validated configuration, not model/user input.
        String mappingSql = "SELECT siss_storeid FROM " + schema
                + ".[t_bd_mtbranch_info] WHERE mt_storeid = ?";
        String countsSql = "SELECT (SELECT COUNT_BIG(*) FROM " + schema
                + ".[t_rm_saleflow_frommail] WHERE mail_orderid = ? AND branch_no = ?) AS sale_count, "
                + "(SELECT COUNT_BIG(*) FROM " + schema
                + ".[t_rm_payflow_frommail] WHERE mail_orderid = ? AND branch_no = ?) AS pay_count";
        try (var connection = pool.getConnection()) {
            connection.setReadOnly(true); // Defence in depth; account MUST have SELECT-only privileges.
            String branch;
            try (var statement = connection.prepareStatement(mappingSql)) {
                statement.setQueryTimeout(route.queryTimeoutSeconds());
                statement.setString(1, storeCode);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new IntegrationException("未找到美团门店到思迅门店的映射，未查询流水。");
                    branch = result.getString(1);
                    if (branch == null || branch.isBlank() || result.next()) {
                        throw new IntegrationException("思迅门店映射为空或不唯一，需要核实配置。");
                    }
                    branch = branch.trim();
                }
            }
            try (var statement = connection.prepareStatement(countsSql)) {
                statement.setQueryTimeout(route.queryTimeoutSeconds());
                statement.setString(1, orderId);
                statement.setString(2, branch);
                statement.setString(3, orderId);
                statement.setString(4, branch);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new IntegrationException("思迅查询未返回计数，不能视为零条记录。");
                    return new PosFlowSnapshot(result.getLong("sale_count"), result.getLong("pay_count"), Instant.now());
                }
            }
        } catch (SQLException e) {
            throw new IntegrationException("思迅只读查询失败，无法确认流水状态；请检查连接、权限及表结构。");
        }
    }

    private HikariDataSource createPool(RetailOpsProperties.PosRoute route) {
        if (!route.jdbcUrl().startsWith("jdbc:sqlserver://")) {
            throw new IntegrationException("当前适配器只支持已配置的SQL Server数据源。");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(route.jdbcUrl());
        config.setUsername(route.username());
        config.setPassword(route.password());
        config.setReadOnly(true);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("loginTimeout", "5");
        config.addDataSourceProperty("socketTimeout", "15000");
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void close() { pools.values().forEach(HikariDataSource::close); }
}
