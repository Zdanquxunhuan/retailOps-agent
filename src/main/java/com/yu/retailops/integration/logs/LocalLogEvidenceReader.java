package com.yu.retailops.integration.logs;

import com.yu.retailops.config.DiagnosisV2Properties;
import com.yu.retailops.domain.DiagnosisDetails.LogMatch;
import com.yu.retailops.domain.DiagnosisDetails.LogSearch;
import com.yu.retailops.security.StoreAccessPolicy;
import com.yu.retailops.support.ContentHash;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Reads administrator-mounted files only; no shell, SSH, recursive paths or model arguments. */
@Component
public class LocalLogEvidenceReader {
    private static final Pattern ORDER = field("orderId|order_id|mailOrderId|mail_orderid");
    private static final Pattern STORE = field("app_poi_code|appPoiCode");
    private final DiagnosisV2Properties.Logs config;
    private final StoreAccessPolicy access;
    private final Charset charset;

    public LocalLogEvidenceReader(DiagnosisV2Properties properties, StoreAccessPolicy access) {
        this.config = properties.logs();
        this.access = access;
        this.charset = Charset.forName(config.charset());
        // Invalid glob/charset is an administrator configuration error, not an empty search.
        FileSystems.getDefault().getPathMatcher("glob:" + config.fileGlob());
    }

    public LogSearch search(String operator, String orderId, String storeCode) {
        access.check(operator, storeCode);
        Instant observed = Instant.now();
        if (!config.enabled()) return new LogSearch("DISABLED", observed, 0, 0, 0,
                List.of("日志读取未启用；没有形成日志证据。"), List.of());
        Scan scan = new Scan(observed);
        List<Path> files = new ArrayList<>();
        Path root = config.directory().toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return scan.unavailable();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            var matcher = root.getFileSystem().getPathMatcher("glob:" + config.fileGlob());
            int visited = 0;
            for (Path path : stream) {
                // Bound directory enumeration as well as file reads, including unrelated entries.
                if (++visited > config.maxFiles() * 10 || scan.expired()) {
                    scan.limit("DIRECTORY_SCAN_LIMIT"); break;
                }
                if (!matcher.matches(path.getFileName())) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    scan.limit("NON_REGULAR_FILE_SKIPPED"); continue;
                }
                if (files.size() == config.maxFiles()) { scan.limit("FILE_LIMIT"); break; }
                files.add(path);
            }
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path path : files) {
                if (scan.stop()) break;
                read(path, scan, orderId, storeCode);
            }
        } catch (IOException | DirectoryIteratorException | SecurityException e) {
            scan.limit("FILE_ACCESS_FAILED");
        }
        if (scan.files == 0) return scan.unavailable();
        return scan.result();
    }

    private void read(Path file, Scan scan, String order, String store) {
        try {
            BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile()) { scan.limit("NON_REGULAR_FILE_SKIPPED"); return; }
            try (InputStream input = new BufferedInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS))) {
                scan.files++;
                String source = "file-" + ContentHash.sha256(file.getFileName().toString()).substring(0, 12);
                long remaining = before.size();
                long lineNumber = 0;
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                boolean oversized = false;
                boolean stopped = false;
                while (remaining > 0) {
                    if (scan.stop()) { stopped = true; break; }
                    int value = input.read();
                    if (value < 0) { scan.limit("FILE_CHANGED"); stopped = true; break; }
                    remaining--;
                    scan.bytes++;
                    if (value == '\n') {
                        scan.lines++;
                        lineNumber++;
                        if (!oversized) match(line.toByteArray(), scan, source, lineNumber,
                                before.lastModifiedTime().toInstant(), order, store);
                        line.reset();
                        oversized = false;
                    } else if (line.size() < config.maxLineBytes()) {
                        line.write(value);
                    } else {
                        oversized = true;
                        scan.limit("OVERSIZED_LINE_SKIPPED");
                    }
                }
                if (!stopped && line.size() > 0) {
                    // Unterminated final records may still be being written: do not treat as evidence.
                    scan.limit("UNTERMINATED_LINE_SKIPPED");
                }
            }
            BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!Objects.equals(before.fileKey(), after.fileKey()) || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) scan.limit("FILE_CHANGED");
        } catch (IOException | SecurityException e) { scan.limit("FILE_ACCESS_FAILED"); }
    }

    private void match(byte[] bytes, Scan scan, String source, long lineNumber,
                       Instant modified, String order, String store) {
        String text;
        try {
            text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { scan.limit("UNDECODABLE_LINE_SKIPPED"); return; }
        // Require explicit exact order + Meituan store on the SAME line. Never infer a store from filenames.
        if (!values(ORDER, text).equals(Set.of(order)) || !values(STORE, text).equals(Set.of(store))) return;
        String category = text.contains("SQLException") || text.contains("SQLServerException")
                ? "DATABASE_EXCEPTION_MARKER"
                : text.contains("SocketTimeoutException") || text.contains("ConnectTimeoutException")
                ? "NETWORK_TIMEOUT_MARKER" : "ORDER_MENTION";
        scan.matches.add(new LogMatch("L" + (scan.matches.size() + 1), source, lineNumber,
                modified, ContentHash.sha256(bytes), category));
    }

    private static Pattern field(String names) {
        return Pattern.compile("(?<![A-Za-z0-9_])[\"']?(?:" + names
                + ")[\"']?\\s*[:=]\\s*[\"']?([^\\s\"',;{}\\[\\]()]+)");
    }

    private static Set<String> values(Pattern pattern, String text) {
        Set<String> result = new HashSet<>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private final class Scan {
        private final Instant observed;
        private final long deadline = System.nanoTime() + config.timeoutMillis() * 1_000_000L;
        private final List<LogMatch> matches = new ArrayList<>();
        private final Set<String> limits = new LinkedHashSet<>();
        private int files;
        private long bytes;
        private long lines;

        private Scan(Instant observed) { this.observed = observed; }
        private void limit(String reason) { limits.add(reason); }
        private boolean expired() { return System.nanoTime() - deadline >= 0; }
        private boolean stop() {
            if (expired()) { limit("TIME_BUDGET"); return true; }
            if (bytes >= config.maxBytes()) { limit("BYTE_LIMIT"); return true; }
            if (matches.size() >= config.maxMatches()) { limit("MATCH_LIMIT"); return true; }
            return false;
        }
        private LogSearch unavailable() {
            limit("NO_READABLE_FILES");
            return build("UNAVAILABLE");
        }
        private LogSearch result() {
            return build(!limits.isEmpty() ? "PARTIAL" : matches.isEmpty() ? "NO_MATCH" : "MATCHED");
        }
        private LogSearch build(String status) {
            List<String> notes = new ArrayList<>(limits);
            notes.add("仅扫描配置目录中的有界文件集合；未验证30天留存或完整时间覆盖。未命中不能证明未接单。"
                    + "仅同一行同时包含明确订单号和美团门店字段时取证；异常关键词不等于根因。"
                    + "fileModifiedAt是文件修改时间，不是业务事件时间。source是文件名SHA-256的前12位别名。");
            return new LogSearch(status, observed, files, bytes, lines, List.copyOf(notes), List.copyOf(matches));
        }
    }
}
