package com.yu.retailops.workflow;

import com.yu.retailops.config.RetailOpsProperties;
import com.yu.retailops.domain.DiagnosisReport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

/** Single-instance local durable journal; no raw prompts or upstream payloads are persisted. */
@Component
public class DiagnosisStore {
    private final Path directory;
    private final JsonMapper json = JsonMapper.builder().build();

    public DiagnosisStore(RetailOpsProperties properties) {
        directory = properties.dataDirectory().toAbsolutePath().normalize();
        try { Files.createDirectories(directory); }
        catch (IOException e) { throw new IllegalStateException("诊断审计目录不可写"); }
    }

    public void start(UUID task, String owner, Instant started) {
        write(task, new StoredTask(owner, "RUNNING", started, null, null));
    }

    public void complete(String owner, DiagnosisReport report) {
        write(report.taskId(), new StoredTask(owner, "COMPLETED", report.startedAt(), report, null));
    }

    public void fail(UUID task, String owner, Instant started, String safeCode) {
        write(task, new StoredTask(owner, "FAILED", started, null, safeCode));
    }

    public StoredTask read(UUID task, String owner) {
        Path file = directory.resolve(task + ".json");
        if (!Files.isRegularFile(file)) return null;
        try {
            StoredTask stored = json.readValue(Files.readString(file), StoredTask.class);
            if (!owner.equals(stored.owner())) throw new AccessDeniedException("无权查看该诊断任务");
            return stored;
        } catch (IOException e) { throw new IllegalStateException("诊断记录读取失败"); }
    }

    private void write(UUID task, StoredTask value) {
        Path temp = null;
        try {
            byte[] bytes = json.writeValueAsBytes(value);
            temp = Files.createTempFile(directory, "." + task, ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Require atomic replacement; never expose partially written audit records.
            Files.move(temp, directory.resolve(task + ".json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("诊断审计写入失败");
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    public record StoredTask(String owner, String state, Instant startedAt,
                             DiagnosisReport report, String failureCode) { }
}
