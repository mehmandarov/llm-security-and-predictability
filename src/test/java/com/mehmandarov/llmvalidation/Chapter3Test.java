package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Chapter 3: The Hallucination (Deterministic Validation)")
class Chapter3Test {

    private final StrictValidator validator = new StrictValidator();

    @Test
    @DisplayName("should catch hallucinated future dates (Temporal Rule)")
    void shouldCatchHallucinations() {
        // Arrange
        ExtractedInvoice hallucinatedInvoice = new ExtractedInvoice(
            "INV-FUTURE-001", 
            LocalDate.of(2050, 1, 1), 
            new BigDecimal("1000000.00"), 
            "USD", 
            null,
            List.of()
        );
        
        // Act
        ValidationResult result = validator.validate(hallucinatedInvoice);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.message().contains("future"));
    }

    @Test
    @DisplayName("should catch math errors (Business Logic)")
    void shouldCatchMathError() {
        // Arrange
        // Items sum to 300, Total is 5000
        ExtractedInvoice.LineItem item1 = new ExtractedInvoice.LineItem("A", 1, new BigDecimal("100.00"));
        ExtractedInvoice.LineItem item2 = new ExtractedInvoice.LineItem("B", 1, new BigDecimal("200.00"));
        
        ExtractedInvoice badMathInvoice = new ExtractedInvoice(
            "INV-MATH-001", 
            LocalDate.now(), 
            new BigDecimal("5000.00"), 
            "USD", 
            null,
            List.of(item1, item2)
        );

        // Act
        ValidationResult result = validator.validate(badMathInvoice);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.category().equals("BUSINESS"));
    }

    @Test
    @DisplayName("should catch missing required fields (Jakarta Schema)")
    void shouldCatchMissingFields() {
        // Arrange — null invoice number, null amount, null currency
        ExtractedInvoice incomplete = new ExtractedInvoice(
            null, 
            LocalDate.now(), 
            null, 
            null, 
            null,
            List.of()
        );

        // Act
        ValidationResult result = validator.validate(incomplete);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.category().equals("SCHEMA"));
    }

    @Test
    @DisplayName("should pass a completely valid invoice")
    void shouldPassValidInvoice() {
        // Arrange
        ExtractedInvoice.LineItem item = new ExtractedInvoice.LineItem("Consulting", 2, new BigDecimal("500.00"));
        ExtractedInvoice valid = new ExtractedInvoice(
            "INV-VALID-001",
            LocalDate.now(),
            new BigDecimal("1000.00"),
            "USD",
            null,
            List.of(item)
        );

        // Act
        ValidationResult result = validator.validate(valid);

        // Assert
        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
}
