package com.mehmandarov.llmvalidation.chapter4_correction;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 4: The Bargaining (Self-Correction).
 * Uses the validation errors to re-prompt the LLM.
 */
public class CorrectiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(CorrectiveExtractor.class);
    private final SimpleInvoiceExtractor extractor;
    private final StrictValidator validator;
    private final int maxRetries;

    public CorrectiveExtractor(SimpleInvoiceExtractor extractor, StrictValidator validator, int maxRetries) {
        this.extractor = extractor;
        this.validator = validator;
        this.maxRetries = maxRetries;
    }

    public ExtractedInvoice extract(String text) {
        log.info("📝 Initial extraction attempt...");
        ExtractedInvoice invoice = extractor.extract(text);
        ValidationResult result = validator.validate(invoice);
        
        int attempts = 0;
        while (!result.isValid() && attempts < maxRetries) {
            attempts++;
            log.warn("🔄 FEEDBACK LOOP (Attempt {}/{}): Asking LLM to fix errors...", attempts, maxRetries);
            
            String feedbackPrompt = buildFeedbackPrompt(text, invoice, result);
            invoice = extractor.extract(feedbackPrompt);
            result = validator.validate(invoice);
        }
        
        if (result.isValid()) {
            log.info("✅ Extraction SUCCESS after {} corrections.", attempts);
        } else {
            log.error("❌ Extraction FAILED after {} attempts. Returning best effort.", attempts);
        }
        
        return invoice;
    }

    private String buildFeedbackPrompt(String originalText, ExtractedInvoice wrongInvoice, ValidationResult violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("You previously extracted this data:\n").append(wrongInvoice).append("\n\n");
        sb.append("The following validation errors were found:\n");
        violations.errors().forEach(e -> sb.append("- ").append(e.message()).append("\n"));
        sb.append("\nPlease re-extract the data from the text below, fixing these specific errors:\n");
        sb.append(originalText);
        return sb.toString();
    }
}
