package com.yu.retailops.knowledge;

import com.yu.retailops.config.DiagnosisV2Properties;
import com.yu.retailops.domain.DiagnosisDetails.*;
import com.yu.retailops.domain.DiagnosisReport;
import com.yu.retailops.security.StoreAccessPolicy;
import com.yu.retailops.support.ContentHash;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.*;

/** Versioned, administrator-reviewed SOP retrieval by exact outcome and optional log markers. */
@Component
public class LocalKnowledgeRetriever {
    private static final int MAX_CATALOG_BYTES = 1024 * 1024;
    private static final Set<String> CATEGORIES = Set.of("DATABASE_EXCEPTION_MARKER", "NETWORK_TIMEOUT_MARKER", "ORDER_MENTION");
    private final DiagnosisV2Properties.Knowledge config;
    private final StoreAccessPolicy access;
    private final Map<String, Entry> catalog;
    private final String catalogHash;

    public LocalKnowledgeRetriever(DiagnosisV2Properties properties, StoreAccessPolicy access, ResourceLoader resources) {
        this.config = properties.knowledge();
        this.access = access;
        Map<String, Entry> entries = new LinkedHashMap<>();
        String hash = null;
        if (config.enabled()) {
            // No URL fetch: catalog changes are local administrator actions, loaded at startup.
            if (!config.location().startsWith("classpath:") && !config.location().startsWith("file:")) {
                throw new IllegalStateException("知识目录仅支持classpath:或file:本地资源");
            }
            try (var input = resources.getResource(config.location()).getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_CATALOG_BYTES + 1);
                if (bytes.length > MAX_CATALOG_BYTES) throw new IllegalArgumentException();
                Entry[] parsed = JsonMapper.builder().build().readValue(bytes, Entry[].class);
                if (parsed == null || parsed.length == 0 || parsed.length > config.maxEntries()) throw new IllegalArgumentException();
                for (Entry entry : parsed) {
                    validate(entry);
                    Entry immutable = new Entry(entry.id(), entry.version(), entry.title(), entry.source(),
                            entry.shared(), Set.copyOf(entry.stores()), Set.copyOf(entry.outcomes()),
                            Set.copyOf(entry.logCategories()), entry.explanation(), entry.nextCheck());
                    if (entries.putIfAbsent(entry.id(), immutable) != null) throw new IllegalArgumentException();
                }
                hash = ContentHash.sha256(bytes);
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("知识目录加载失败：请核对本地文件、大小、字段及唯一ID；未回退至其他数据");
            }
        }
        catalog = Collections.unmodifiableMap(entries);
        catalogHash = hash;
    }

    public KnowledgeSearch retrieve(String operator, DiagnosisReport report, LogSearch logs) {
        access.check(operator, report.storeCode());
        if (!config.enabled()) return new KnowledgeSearch("DISABLED", null, List.of());
        List<KnowledgeHit> hits = new ArrayList<>();
        for (Entry entry : catalog.values()) {
            // ACL precedes relevance selection. Never retrieve another store's document into the prompt.
            if (!visible(entry, report.storeCode()) || !entry.outcomes().contains(report.outcome().name())) continue;
            List<String> logIds = logs.matches().stream()
                    .filter(m -> entry.logCategories().contains(m.category())).map(LogMatch::id).toList();
            if (!entry.logCategories().isEmpty() && logIds.isEmpty()) continue;
            List<String> evidenceIds = new ArrayList<>();
            report.evidence().stream().filter(e -> "E3".equals(e.id())).map(DiagnosisReport.Evidence::id)
                    .forEach(evidenceIds::add);
            evidenceIds.addAll(logIds.stream().limit(3).toList());
            if (evidenceIds.isEmpty()) continue;
            hits.add(hit(entry, evidenceIds));
        }
        // Marker-specific guidance first; remaining ties are stable by ID. This is not vector ranking.
        hits.sort(Comparator.comparingInt((KnowledgeHit h) -> catalog.get(h.id()).logCategories().isEmpty() ? 1 : 0)
                .thenComparing(KnowledgeHit::id));
        return new KnowledgeSearch(hits.isEmpty() ? "NO_MATCH" : "RETRIEVED", catalogHash,
                List.copyOf(hits.subList(0, Math.min(hits.size(), config.topK()))));
    }

    public void checkCitations(String operator, String storeCode, List<KnowledgeHit> hits) {
        access.check(operator, storeCode);
        for (KnowledgeHit hit : hits) {
            Entry current = catalog.get(hit.id());
            if (current == null || !visible(current, storeCode)
                    || !current.version().equals(hit.version()) || !digest(current).equals(hit.contentSha256())) {
                throw new AccessDeniedException("知识引用已撤回、权限变化或内容更新，请重新诊断");
            }
        }
    }

    private KnowledgeHit hit(Entry entry, List<String> evidence) {
        return new KnowledgeHit(entry.id(), entry.version(), entry.title(), entry.source(), digest(entry),
                entry.explanation(), entry.nextCheck(), List.copyOf(evidence));
    }

    private String digest(Entry entry) {
        // Hash the rendered content, not merely its title or an upstream mutable URL.
        return ContentHash.sha256(entry.id() + "\n" + entry.version() + "\n" + entry.title() + "\n"
                + entry.source() + "\n" + entry.explanation() + "\n" + entry.nextCheck());
    }

    private boolean visible(Entry entry, String storeCode) {
        return entry.shared() || entry.stores().contains(storeCode);
    }

    private void validate(Entry e) {
        if (e == null || e.id() == null || !e.id().matches("K[0-9]{2,6}")
                || !text(e.version(), 40) || !text(e.title(), 120) || !text(e.source(), 200)
                || !e.source().matches("[A-Za-z0-9_./-]+") || e.source().contains("..")
                || !text(e.explanation(), 800) || !text(e.nextCheck(), 500)
                || e.stores() == null || e.outcomes() == null || e.outcomes().isEmpty()
                || e.logCategories() == null || !CATEGORIES.containsAll(e.logCategories())
                || (e.shared() && !e.stores().isEmpty()) || (!e.shared() && e.stores().isEmpty())) {
            throw new IllegalArgumentException();
        }
        for (String store : e.stores()) if (!text(store, 100) || store.equals("*")) throw new IllegalArgumentException();
        for (String outcome : e.outcomes()) DiagnosisReport.Outcome.valueOf(outcome);
    }

    private boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    public record Entry(String id, String version, String title, String source, boolean shared,
                        Set<String> stores, Set<String> outcomes, Set<String> logCategories,
                        String explanation, String nextCheck) { }
}
