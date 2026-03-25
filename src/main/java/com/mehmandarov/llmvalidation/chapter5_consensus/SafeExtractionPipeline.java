package com.mehmandarov.llmvalidation.chapter5_consensus;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter4_correction.CorrectiveExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 5: The Safe Extraction Pipeline — Fail Safely and Predictably.
 *
 * <p>Chains the entire defense stack (extract → validate → self-correct)
 * and wraps the result in a typed {@link ExtractionOutcome} so the caller
 * always knows whether to trust, review, or reject the data.</p>
 *
 * <p>This is the "capstone" of the talk: we didn't make the LLM perfect —
 * we built a system that tells us <em>when it's not</em>.</p>
 */
public class SafeExtractionPipeline {

    private static final Logger log = LoggerFactory.getLogger(SafeExtractionPipeline.class);

    private final CorrectiveExtractor correctiveExtractor;
    private final StrictValidator validator;

    public SafeExtractionPipeline(SimpleInvoiceExtractor extractor, StrictValidator validator, int maxRetries) {
        this.validator = validator;
        this.correctiveExtractor = new CorrectiveExtractor(extractor, validator, maxRetries);
    }

    /**
     * Runs the full pipeline: extract → validate → self-correct → final verdict.
     *
     * @param text raw invoice text
     * @return a typed outcome: {@code ACCEPTED}, {@code NEEDS_REVIEW}, or {@code REJECTED}
     */
    public ExtractionOutcome process(String text) {
        log.info("🏗️ SafeExtractionPipeline: starting...");

        try {
            ExtractedInvoice invoice = correctiveExtractor.extract(text);
            ValidationResult validation = validator.validate(invoice);

            if (validation.isValid()) {
                log.info("✅ Pipeline result: ACCEPTED");
                return new ExtractionOutcome(Status.ACCEPTED, invoice, null);
            } else {
                log.warn("⚠️ Pipeline result: NEEDS_REVIEW — {} error(s) remain after retries.",
                        validation.errors().size());
                String reason = validation.errors().stream()
                        .map(e -> "[" + e.category() + "] " + e.message())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Unknown errors");
                return new ExtractionOutcome(Status.NEEDS_REVIEW, invoice, reason);
            }
        } catch (Exception e) {
            log.error("❌ Pipeline result: REJECTED — {}", e.getMessage());
            return new ExtractionOutcome(Status.REJECTED, null, e.getMessage());
        }
    }

    public enum Status {
        /** Data extracted and fully validated. Safe to process automatically. */
        ACCEPTED,
        /** Data extracted but validation failed after retries. Route to human review. */
        NEEDS_REVIEW,
        /** Extraction failed entirely (e.g. guardrail blocked, model error). Reject. */
        REJECTED
    }

    public record ExtractionOutcome(Status status, ExtractedInvoice invoice, String reason) {}
}

