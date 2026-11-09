package com.yu.retailops.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties("retailops.diagnostics")
public record DiagnosisV2Properties(@NotNull @Valid Logs logs,
                                    @NotNull @Valid Knowledge knowledge,
                                    boolean explanationEnabled) {
    public record Logs(boolean enabled, @NotNull Path directory,
                       @NotBlank String fileGlob, @NotBlank String charset,
                       @Min(1) @Max(200) int maxFiles,
                       @Min(1024) @Max(104857600) long maxBytes,
                       @Min(256) @Max(65536) int maxLineBytes,
                       @Min(1) @Max(100) int maxMatches,
                       @Min(100) @Max(30000) int timeoutMillis) { }

    public record Knowledge(boolean enabled, @NotBlank String location,
                            @Min(1) @Max(500) int maxEntries,
                            @Min(1) @Max(5) int topK) { }
}
