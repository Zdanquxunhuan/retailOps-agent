package com.yu.retailops.workflow;

import com.yu.retailops.agent.EvidenceExplanationSelector;
import com.yu.retailops.domain.DiagnosisDetails;
import com.yu.retailops.domain.DiagnosisReport;
import com.yu.retailops.integration.logs.LocalLogEvidenceReader;
import com.yu.retailops.knowledge.LocalKnowledgeRetriever;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisEnrichment {
    private final LocalLogEvidenceReader logs;
    private final LocalKnowledgeRetriever knowledge;
    private final EvidenceExplanationSelector selector;

    public DiagnosisEnrichment(LocalLogEvidenceReader logs, LocalKnowledgeRetriever knowledge,
                              EvidenceExplanationSelector selector) {
        this.logs = logs;
        this.knowledge = knowledge;
        this.selector = selector;
    }

    public DiagnosisReport enrich(String operator, DiagnosisReport report) {
        // Unknown store (including 806 / query-window failures) must not unlock local searches.
        if (report.storeCode() == null) return report;
        var logEvidence = logs.search(operator, report.orderId(), report.storeCode());
        var retrieved = knowledge.retrieve(operator, report, logEvidence);
        knowledge.checkCitations(operator, report.storeCode(), retrieved.hits());
        var explanation = selector.select(report, logEvidence, retrieved);
        knowledge.checkCitations(operator, report.storeCode(), explanation.items());
        return report.withDetails(new DiagnosisDetails("2", logEvidence, retrieved, explanation));
    }

    public void checkStoredCitations(String operator, DiagnosisReport report) {
        if (report.details() == null) return; // V1 record compatibility.
        knowledge.checkCitations(operator, report.storeCode(), report.details().knowledge().hits());
        knowledge.checkCitations(operator, report.storeCode(), report.details().explanation().items());
    }
}
