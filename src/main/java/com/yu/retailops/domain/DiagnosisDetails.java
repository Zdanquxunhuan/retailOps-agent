package com.yu.retailops.domain;

import java.time.Instant;
import java.util.List;

/** V2 additive fields. Old persisted reports may have a null details field. */
public record DiagnosisDetails(String version, LogSearch logs,
                               KnowledgeSearch knowledge, Explanation explanation) {
    public record LogSearch(String status, Instant observedAt, int filesScanned,
                            long bytesScanned, long linesScanned,
                            List<String> limitations, List<LogMatch> matches) { }
    /** No raw log text, file paths, customer fields or request signatures. */
    public record LogMatch(String id, String source, long lineNumber,
                           Instant fileModifiedAt, String lineSha256, String category) { }
    public record KnowledgeSearch(String status, String catalogSha256,
                                  List<KnowledgeHit> hits) { }
    public record KnowledgeHit(String id, String version, String title, String source,
                               String contentSha256, String explanation, String nextCheck,
                               List<String> evidenceIds) { }
    public record Explanation(String status, String notice, List<KnowledgeHit> items) { }
}
