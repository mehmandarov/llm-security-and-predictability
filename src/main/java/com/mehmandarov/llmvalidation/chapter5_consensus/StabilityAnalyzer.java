package com.mehmandarov.llmvalidation.chapter5_consensus;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chapter 5: Variance Measurement.
 *
 * <p>Run extraction N times, compare results field-by-field, compute a stability
 * score. Turns vibes into numbers. Quantifies how deterministic a model actually is
 * for a given input.</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Accept a list of {@link ExtractedInvoice} results (from N repeated extractions).</li>
 *   <li>For each field, find the <b>mode</b> (most common value) and compute
 *       <b>agreement %</b> = count(mode) / N.</li>
 *   <li>Average all per-field agreements into an <b>overall stability score</b>.</li>
 * </ol>
 */
public class StabilityAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(StabilityAnalyzer.class);

    /**
     * Analyzes a set of repeated extraction results and returns a stability report.
     *
     * @param results list of invoices extracted from the same input (N runs)
     * @return a report with per-field agreement and overall stability score
     * @throws IllegalArgumentException if results is null or empty
     */
    public StabilityReport analyze(List<ExtractedInvoice> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Need at least one result to analyze.");
        }

        int n = results.size();
        log.info("📊 Analyzing stability across {} extraction runs...", n);

        Map<String, FieldStability> fields = new LinkedHashMap<>();

        fields.put("invoiceNumber", computeStability(
                results.stream().map(ExtractedInvoice::invoiceNumber).toList(), n));
        fields.put("date", computeStability(
                results.stream().map(r -> r.date() != null ? r.date().toString() : null).toList(), n));
        fields.put("amount", computeStability(
                results.stream().map(r -> r.amount() != null ? r.amount().toPlainString() : null).toList(), n));
        fields.put("currency", computeStability(
                results.stream().map(ExtractedInvoice::currency).toList(), n));

        double overallStability = fields.values().stream()
                .mapToDouble(FieldStability::agreement)
                .average()
                .orElse(0.0);

        StabilityReport report = new StabilityReport(fields, overallStability);

        // Log the report
        log.info("📊 Stability Report:");
        fields.forEach((field, stability) ->
                log.info("   {} → dominant='{}', agreement={}%",
                        field, stability.dominantValue(), (int) (stability.agreement() * 100)));
        log.info("   ─────────────────────────────");
        log.info("   Overall stability: {}%", (int) (overallStability * 100));

        return report;
    }

    private FieldStability computeStability(List<String> values, int total) {
        // Group by value, find the mode
        Map<String, Long> counts = values.stream()
                .map(v -> v != null ? v : "<null>")
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        Map.Entry<String, Long> mode = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(Map.entry("<null>", 0L));

        double agreement = (double) mode.getValue() / total;
        String dominantValue = mode.getKey();

        return new FieldStability(dominantValue, agreement);
    }

    /**
     * Per-field stability: what value appeared most often and how many runs agreed.
     */
    public record FieldStability(String dominantValue, double agreement) {}

    /**
     * Full stability report across all fields.
     */
    public record StabilityReport(
            Map<String, FieldStability> fieldReports,
            double overallStability
    ) {}
}

