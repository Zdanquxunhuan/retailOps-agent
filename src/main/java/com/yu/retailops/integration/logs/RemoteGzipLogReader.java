package com.yu.retailops.integration.logs;

import com.jcraft.jsch.*;
import com.yu.retailops.config.DiagnosisV3Properties;
import com.yu.retailops.domain.DiagnosisDetails.*;
import com.yu.retailops.domain.RemoteLogEvidence;
import com.yu.retailops.security.StoreAccessPolicy;
import com.yu.retailops.support.ContentHash;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** SFTP read operations only. No shell channel, exec command, remote writes or local raw-log files. */
@Component
public class RemoteGzipLogReader {
    private static final Pattern ORDER = field("orderId|order_id|mailOrderId|mail_orderid|order");
    private static final Pattern STORE = field("app_poi_code|appPoiCode");
    private static final Pattern TIMESTAMP = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2})(?:[.,](\\d{3}))?");
    private static final String PREFIX = "b-push-center-server.log.";
    private final DiagnosisV3Properties.RemoteLogs config;
    private final StoreAccessPolicy access;
    private final Charset charset;
    private final ZoneId zone;

    public RemoteGzipLogReader(DiagnosisV3Properties properties, StoreAccessPolicy access) {
        config = properties.remoteLogs();
        this.access = access;
        charset = Charset.forName(config.charset());
        zone = ZoneId.of(config.zoneId());
        if (!config.directory().matches("/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("SFTP目录必须是管理员配置的绝对路径，禁止通配符及相对路径");
        }
        if (config.enabled() && (config.host() == null || !config.host().matches("[A-Za-z0-9.:-]+")
                || config.username() == null || !config.username().matches("[A-Za-z0-9_-]+")
                || config.privateKey() == null || !Files.isRegularFile(config.privateKey())
                || config.knownHosts() == null || !Files.isRegularFile(config.knownHosts()))) {
            throw new IllegalStateException("远程日志启用时须配置固定主机、专用账号、私钥及预先核验的known_hosts");
        }
    }

    public RemoteLogEvidence search(String operator, String order, String store, LocalDate date) {
        access.check(operator, store);
        if (date == null) return RemoteLogEvidence.empty("NEED_INPUT", null, "请提供订单日期YYYY-MM-DD；模型不得猜测日期。");
        if (date.isAfter(LocalDate.now(zone)) || date.getYear() < 2000)
            return RemoteLogEvidence.empty("NEED_INPUT", date, "订单日期须为2000年以后且不晚于服务器日志时区的今天。");
        if (order == null || !order.matches("[0-9]{10,32}")) throw new IllegalArgumentException("订单身份无效");
        if (!config.enabled()) return RemoteLogEvidence.empty("DISABLED", date, "远程日志读取未启用。");
        Scan scan = new Scan(date, order);
        Session session = null;
        ChannelSftp sftp = null;
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-read-deadline"); t.setDaemon(true); return t;
        });
        ScheduledFuture<?> cutoff = null;
        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(config.knownHosts().toString());
            jsch.addIdentity(config.privateKey().toString());
            session = jsch.getSession(config.username(), config.host(), config.port());
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "publickey");
            session.setTimeout(Math.min(config.timeoutMillis(), config.connectTimeoutMillis()));
            Session active = session;
            cutoff = timer.schedule(() -> { scan.timedOut = true; active.disconnect(); },
                    config.timeoutMillis(), TimeUnit.MILLISECONDS);
            session.connect(config.connectTimeoutMillis());
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(config.connectTimeoutMillis());
            // Canonical administrator-controlled root. The server must also chroot this account.
            String root = sftp.realpath(config.directory());
            if (!root.equals(config.directory())) throw new IOException("Unexpected root");
            List<Archive> archives = new ArrayList<>();
            int[] visited = {0};
            // Callback enumeration avoids accumulating an unbounded ls result in Java memory.
            sftp.ls(root, entry -> {
                if (++visited[0] > config.maxDirectoryEntries() || scan.expired()) {
                    scan.limit("DIRECTORY_SCAN_LIMIT"); return ChannelSftp.LsEntrySelector.BREAK;
                }
                String name = entry.getFilename();
                for (LocalDate day : scan.dates) {
                    if (name.matches(Pattern.quote(PREFIX + day + ".") + "[A-Za-z0-9_-]+\\.gz")) {
                        if (!entry.getAttrs().isReg()) { scan.limit("NON_REGULAR_ARCHIVE_SKIPPED"); break; }
                        archives.add(new Archive(name, day)); break;
                    }
                }
                return ChannelSftp.LsEntrySelector.CONTINUE;
            });
            // Order day first, then previous/next day. Stable within each date.
            archives.sort(Comparator.comparingInt((Archive a) -> scan.dates.indexOf(a.day()))
                    .thenComparing(Archive::name));
            scan.missing.addAll(scan.dates.stream().filter(d -> archives.stream().noneMatch(a -> a.day().equals(d))).toList());
            if (!scan.missing.isEmpty()) scan.limit("DATE_WITHOUT_ARCHIVE");
            if (archives.size() > config.maxFiles()) scan.limit("FILE_LIMIT");
            for (Archive archive : archives.subList(0, Math.min(archives.size(), config.maxFiles()))) {
                if (scan.stop()) break;
                read(sftp, root, archive, scan, order, store);
            }
        } catch (JSchException | SftpException | IOException | IllegalArgumentException e) {
            scan.limit(scan.expired() ? "TIME_LIMIT" : "SFTP_ACCESS_FAILED");
        } finally {
            if (cutoff != null) cutoff.cancel(false);
            if (sftp != null) sftp.disconnect();
            if (session != null) session.disconnect();
            timer.shutdownNow();
        }
        if (scan.expired()) scan.limit("TIME_LIMIT");
        access.check(operator, store);
        return scan.result();
    }

    private void read(ChannelSftp sftp, String root, Archive archive, Scan scan, String order, String store) {
        String path = root + "/" + archive.name();
        try {
            SftpATTRS before = sftp.lstat(path);
            if (!before.isReg() || !sftp.realpath(path).equals(path)) {
                scan.limit("NON_REGULAR_ARCHIVE_SKIPPED"); return;
            }
            if (before.getSize() > config.maxCompressedBytes() - scan.compressed) {
                scan.limit("COMPRESSED_FILE_TOO_LARGE"); return;
            }
            scan.files++;
            try (InputStream remote = sftp.get(path);
                 InputStream bounded = new FilterInputStream(remote) {
                     @Override public int read() throws IOException {
                         byte[] b = new byte[1]; int n = read(b, 0, 1); return n < 0 ? -1 : b[0] & 255;
                     }
                     @Override public int read(byte[] bytes, int off, int len) throws IOException {
                         long remaining = config.maxCompressedBytes() - scan.compressed;
                         if (scan.expired() || remaining <= 0) {
                             scan.limit(scan.expired() ? "TIME_LIMIT" : "COMPRESSED_BYTE_LIMIT");
                             throw new IOException("Read budget exhausted");
                         }
                         int n = in.read(bytes, off, (int) Math.min(len, remaining));
                         if (n > 0) scan.compressed += n;
                         return n;
                     }
                 };
                 InputStream gzip = new GZIPInputStream(bounded, 8192)) {
                String source = "archive-" + ContentHash.sha256(archive.name()).substring(0, 12);
                Instant modified = Instant.ofEpochSecond(Integer.toUnsignedLong(before.getMTime()));
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long lineNumber = 0;
                int after = 0;
                boolean oversized = false;
                while (!scan.stop()) {
                    int n = gzip.read(buffer, 0, (int) Math.min(buffer.length, config.maxExpandedBytes() - scan.expanded));
                    if (n < 0) break;
                    scan.expanded += n;
                    for (int i = 0; i < n; i++) {
                        if (scan.expired() || scan.matches.size() >= config.maxMatches()) break;
                        int b = buffer[i] & 255;
                        if (b == '\n') {
                            lineNumber++; scan.lines++;
                            if (after > 0) { scan.contextLines++; after--; }
                            if (!oversized && match(line.toByteArray(), scan, source, lineNumber, modified, order, store))
                                after = config.afterLines();
                            line.reset(); oversized = false;
                        } else if (line.size() < config.maxLineBytes()) line.write(b);
                        else { oversized = true; scan.limit("OVERSIZED_LINE_SKIPPED"); }
                    }
                }
                if (line.size() > 0) scan.limit("UNTERMINATED_OR_TRUNCATED_LINE_SKIPPED");
            }
            SftpATTRS after = sftp.lstat(path);
            if (!after.isReg() || before.getSize() != after.getSize() || before.getMTime() != after.getMTime())
                scan.limit("ARCHIVE_CHANGED");
        } catch (IOException | SftpException e) {
            scan.limit(scan.expired() ? "TIME_LIMIT" : "ARCHIVE_READ_FAILED");
        }
    }

    private boolean match(byte[] bytes, Scan scan, String source, long lineNo, Instant modified,
                          String order, String store) {
        String text;
        try {
            text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { scan.limit("UNDECODABLE_LINE_SKIPPED"); return false; }
        if (!scan.exactOrder.matcher(text).find()) return false;
        Set<String> orderFields = values(ORDER, text), storeFields = values(STORE, text);
        // Never emit evidence for a line with a conflicting explicit identity (including mixed batches).
        if ((!orderFields.isEmpty() && !orderFields.equals(Set.of(order)))
                || (!storeFields.isEmpty() && !storeFields.equals(Set.of(store)))) return false;
        boolean bound = orderFields.equals(Set.of(order)) && storeFields.equals(Set.of(store));
        String category = bound && (text.contains("SQLException") || text.contains("SQLServerException"))
                ? "DATABASE_EXCEPTION_MARKER" : bound && (text.contains("SocketTimeoutException")
                || text.contains("ConnectTimeoutException")) ? "NETWORK_TIMEOUT_MARKER" : "ORDER_MENTION";
        boolean received = bound && config.callbackReceivedMarker() != null
                && !config.callbackReceivedMarker().isBlank() && text.contains(config.callbackReceivedMarker());
        if (received) scan.received = true;
        String id = "L" + (scan.matches.size() + 1);
        scan.matches.add(new LogMatch(id, source, lineNo, modified, ContentHash.sha256(bytes), category));
        scan.events.add(new RemoteLogEvidence.Event(id, eventTime(text), received ? "CALLBACK_RECEIVED_MARKER"
                : category, bound ? "ORDER_AND_STORE_SAME_LINE" : "ORDER_ID_ONLY_UNCONFIRMED"));
        if (!bound) scan.notes.add("ORDER_ONLY_MATCH_IS_NOT_CALLBACK_PROOF");
        return true;
    }

    private Instant eventTime(String line) {
        var m = TIMESTAMP.matcher(line);
        if (!m.find()) return null;
        try {
            LocalDateTime time = LocalDateTime.parse(m.group(1).replace('T', ' '), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
            if (m.group(2) != null) time = time.withNano(Integer.parseInt(m.group(2)) * 1_000_000);
            return time.atZone(zone).toInstant();
        } catch (DateTimeException e) { return null; }
    }
    private static Pattern field(String names) {
        return Pattern.compile("(?<![A-Za-z0-9_])(?:" + names + ")[\\\"']?\\s*[:=]\\s*[\\\"']?([A-Za-z0-9_-]+)(?![A-Za-z0-9_-])");
    }
    private static Set<String> values(Pattern pattern, String text) {
        Set<String> result = new HashSet<>();
        var m = pattern.matcher(text); while (m.find()) result.add(m.group(1)); return result;
    }
    private record Archive(String name, LocalDate day) { }
    private final class Scan {
        final Instant observed = Instant.now();
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.timeoutMillis());
        final LocalDate date;
        final Pattern exactOrder;
        final List<LocalDate> dates;
        final List<LocalDate> missing = new ArrayList<>();
        final Set<String> limits = new LinkedHashSet<>(), notes = new LinkedHashSet<>();
        final List<LogMatch> matches = new ArrayList<>();
        final List<RemoteLogEvidence.Event> events = new ArrayList<>();
        long compressed, expanded, lines, contextLines;
        int files;
        boolean received;
        volatile boolean timedOut;
        Scan(LocalDate date, String order) {
            this.date = date;
            dates = List.of(date, date.minusDays(1), date.plusDays(1));
            exactOrder = Pattern.compile("(?<![0-9A-Za-z])" + Pattern.quote(order) + "(?![0-9A-Za-z])");
        }
        boolean expired() { return timedOut || System.nanoTime() >= deadline; }
        void limit(String code) { limits.add(code); }
        boolean stop() {
            if (expired()) limit("TIME_LIMIT");
            if (compressed >= config.maxCompressedBytes()) limit("COMPRESSED_BYTE_LIMIT");
            if (expanded >= config.maxExpandedBytes()) limit("EXPANDED_BYTE_LIMIT");
            if (matches.size() >= config.maxMatches()) limit("MATCH_LIMIT");
            return expired() || compressed >= config.maxCompressedBytes()
                    || expanded >= config.maxExpandedBytes() || matches.size() >= config.maxMatches();
        }
        RemoteLogEvidence result() {
            List<String> limitations = new ArrayList<>(limits); limitations.addAll(notes);
            limitations.add("未命中不证明未接单；日志标记不证明根因，邻近200行不自动属于同一订单。");
            limitations.add("只搜索日期命名的gz归档，不包含当前活动日志；文件名日期不保证事件日期。非原子快照。");
            if (config.callbackReceivedMarker() == null || config.callbackReceivedMarker().isBlank())
                limitations.add("未配置经源码核验的回调接收标记，不能确认美团回调进入本地。");
            events.sort(Comparator.comparing(RemoteLogEvidence.Event::eventTime, Comparator.nullsLast(Comparator.naturalOrder())));
            return new RemoteLogEvidence(new LogSearch(files == 0 ? "UNAVAILABLE" : limits.isEmpty() ? "SCANNED" : "PARTIAL",
                    observed, files, expanded, lines, List.copyOf(limitations), List.copyOf(matches)),
                    date, dates, List.copyOf(missing), compressed, contextLines,
                    received ? "RECEIPT_MARKER_FOUND" : "UNKNOWN", List.copyOf(events));
        }
    }
}
