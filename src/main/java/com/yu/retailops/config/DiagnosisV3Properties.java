package com.yu.retailops.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.nio.file.Path;

@Validated
@ConfigurationProperties("retailops.agent")
public record DiagnosisV3Properties(boolean enabled, boolean plannerEnabled,
        @Min(4) @Max(12) int maxSteps, @Min(10) @Max(180) int timeoutSeconds,
        @NotNull @Valid RemoteLogs remoteLogs) {
    public record RemoteLogs(boolean enabled, String host, @Min(1) @Max(65535) int port,
            String username, Path privateKey, Path knownHosts,
            @NotBlank String directory, @NotBlank String zoneId,
            @NotBlank String charset, @Min(1) @Max(100) int maxFiles,
            @Min(1) @Max(5000) int maxDirectoryEntries,
            @Min(1024) @Max(104857600) long maxCompressedBytes,
            @Min(1024) @Max(268435456) long maxExpandedBytes,
            @Min(256) @Max(262144) int maxLineBytes,
            @Min(1) @Max(100) int maxMatches,
            @Min(1000) @Max(60000) int timeoutMillis,
            @Min(1000) @Max(10000) int connectTimeoutMillis,
            @Min(0) @Max(200) int afterLines,
            @Size(max=200) String callbackReceivedMarker) {
        @Override public String toString() { return "RemoteLogs[connection=REDACTED]"; }
    }
}
