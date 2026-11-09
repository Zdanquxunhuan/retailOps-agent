package com.yu.retailops.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Metadata only: raw lines, paths, customer data and signatures never leave the reader. */
public record RemoteLogEvidence(DiagnosisDetails.LogSearch search, LocalDate orderDate,
        List<LocalDate> requestedDates, List<LocalDate> datesWithoutArchives,
        long compressedBytes, long contextLinesInspected, String callbackReceipt,
        List<Event> timeline) {
    public record Event(String evidenceId, Instant eventTime, String stage, String association) { }

    public static RemoteLogEvidence empty(String status, LocalDate date, String limitation) {
        return new RemoteLogEvidence(new DiagnosisDetails.LogSearch(status, Instant.now(), 0, 0, 0,
                List.of(limitation), List.of()), date, List.of(), List.of(), 0, 0,
                "UNKNOWN", List.of());
    }
}
