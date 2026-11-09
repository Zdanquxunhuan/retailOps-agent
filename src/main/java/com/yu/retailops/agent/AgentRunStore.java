package com.yu.retailops.agent;

import com.yu.retailops.config.RetailOpsProperties;
import com.yu.retailops.domain.AgentDiagnosisRun;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;

/** Single-instance checkpoints. A process epoch distinguishes interrupted work; no auto replay. */
@Component
public class AgentRunStore {
    private final Path directory;
    private final String epoch = UUID.randomUUID().toString();
    private final JsonMapper json = JsonMapper.builder().build();
    public AgentRunStore(RetailOpsProperties properties) {
        directory = properties.dataDirectory().toAbsolutePath().normalize().resolve("agent-v3");
        try { Files.createDirectories(directory); }
        catch (IOException e) { throw new IllegalStateException("Agent任务目录不可写"); }
    }
    public void save(String owner, AgentDiagnosisRun run) {
        Path temp = null;
        try {
            byte[] bytes = json.writeValueAsBytes(new StoredRun(owner, epoch, run));
            temp = Files.createTempFile(directory, "." + run.taskId(), ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, directory.resolve(run.taskId() + ".json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new IllegalStateException("Agent步骤保存失败，停止继续调用工具"); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
    public AgentDiagnosisRun read(UUID id, String owner) {
        Path file = directory.resolve(id + ".json");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        try {
            if (Files.size(file) > 2 * 1024 * 1024) throw new IOException("Oversized record");
            StoredRun saved = json.readValue(Files.readString(file), StoredRun.class);
            if (!owner.equals(saved.owner())) throw new AccessDeniedException("无权查看该任务");
            AgentDiagnosisRun run = saved.run();
            if (!epoch.equals(saved.processEpoch()) && "RUNNING".equals(run.state()))
                return new AgentDiagnosisRun(run.taskId(), "INTERRUPTED", "PROCESS_RESTARTED", run.orderDate(),
                        run.report(), run.remoteLogs(), run.steps(), run.repairPlan());
            return run;
        } catch (IOException e) { throw new IllegalStateException("Agent任务读取失败"); }
    }
    public record StoredRun(String owner, String processEpoch, AgentDiagnosisRun run) { }
}
