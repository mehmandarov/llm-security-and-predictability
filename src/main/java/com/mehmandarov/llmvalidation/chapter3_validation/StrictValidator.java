package com.mehmandarov.llmvalidation.chapter3_validation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.model.ValidationResult.ValidationError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chapter 3: The Hallucination (Deterministic Validation).
 * Three layers of deterministic checks – no LLM involved.
 */
public class StrictValidator {

    private static final Logger log = LoggerFactory.getLogger(StrictValidator.class);
    private final Validator jakartaValidator;

    public StrictValidator() {
        this.jakartaValidator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    public ValidationResult validate(ExtractedInvoice invoice) {
        List<ValidationError> errors = new ArrayList<>();

        // Layer 1: Schema (Jakarta)
        Set<ConstraintViolation<ExtractedInvoice>> violations = jakartaValidator.validate(invoice);
        for (ConstraintViolation<ExtractedInvoice> v : violations) {
            String msg = v.getPropertyPath() + ": " + v.getMessage();
            log.warn("❌ SCHEMA ERROR: {}", msg);
            errors.add(new ValidationError("SCHEMA", msg));
        }

        // Layer 2: Business Logic
        if (invoice.amount() != null && invoice.items() != null && !invoice.items().isEmpty()) {
            BigDecimal calculated = invoice.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (calculated.compareTo(invoice.amount()) != 0) {
                String msg = "Total amount " + invoice.amount() + " does not equal sum of items " + calculated;
                log.warn("❌ BUSINESS ERROR: {}", msg);
                errors.add(new ValidationError("BUSINESS", msg));
            }
        }

        // Layer 3: Pragmatic Sanity Checks
        if (invoice.date() != null) {
            if (invoice.date().isAfter(LocalDate.now())) {
                String msg = "Date " + invoice.date() + " is in the future.";
                log.warn("❌ TEMPORAL ERROR: {}", msg);
                errors.add(new ValidationError("TEMPORAL", msg));
            }
            if (invoice.date().isBefore(LocalDate.now().minusYears(2))) {
                String msg = "Date " + invoice.date() + " is suspiciously old (>2 years).";
                log.warn("❌ TEMPORAL ERROR: {}", msg);
                errors.add(new ValidationError("TEMPORAL", msg));
            }
        }

        if (invoice.amount() != null && invoice.amount().compareTo(new BigDecimal("10000000")) > 0) {
            String msg = "Amount " + invoice.amount() + " exceeds sanity threshold of $10,000,000.";
            log.warn("❌ SANITY ERROR: {}", msg);
            errors.add(new ValidationError("SANITY", msg));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(errors);
        }
        return ValidationResult.valid();
    }
}
