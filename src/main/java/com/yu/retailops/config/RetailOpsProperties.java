package com.yu.retailops.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@Validated
@ConfigurationProperties("retailops")
public record RetailOpsProperties(
        @NotNull Path dataDirectory,
        @NotNull @Valid Meituan meituan,
        @NotEmpty Map<String, @Valid Operator> users,
        @NotEmpty Map<String, @Valid PosRoute> posRoutes,
        @Min(1) @Max(16) int maxConcurrentDiagnoses) {

    public record Meituan(@NotNull Path sdkJar, @NotNull Path sdkConfigDirectory,
                          @NotBlank String appId, @NotBlank String appSecret) {
        @Override public String toString() { return "Meituan[credentials=REDACTED]"; }
    }

    public record Operator(@NotBlank String passwordHash, @NotEmpty Set<String> stores) {
        @Override public String toString() { return "Operator[credentials=REDACTED]"; }
    }

    /** Key in posRoutes is the exact app_poi_code, never supplied as a JDBC URL by the caller. */
    public record PosRoute(@NotBlank String jdbcUrl, @NotBlank String username,
                           @NotBlank String password,
                           @NotBlank @Pattern(regexp="[A-Za-z_][A-Za-z0-9_]*") String schema,
                           @Min(1) @Max(30) int queryTimeoutSeconds) {
        @Override public String toString() { return "PosRoute[connection=REDACTED]"; }
    }
}
