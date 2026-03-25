package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter3_validation.OutputNormalizer;
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

    @Test
    @DisplayName("should reject negative amounts (@Positive)")
    void shouldRejectNegativeAmount() {
        ExtractedInvoice negative = new ExtractedInvoice(
                "INV-NEG-001", LocalDate.now(),
                new BigDecimal("-500.00"), "USD", null, List.of());

        ValidationResult result = validator.validate(negative);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.category().equals("SCHEMA"));
    }

    @Test
    @DisplayName("should reject zero amounts (@Positive)")
    void shouldRejectZeroAmount() {
        ExtractedInvoice zero = new ExtractedInvoice(
                "INV-ZERO-001", LocalDate.now(),
                BigDecimal.ZERO, "USD", null, List.of());

        ValidationResult result = validator.validate(zero);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.category().equals("SCHEMA"));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  OutputNormalizer tests
    // ─────────────────────────────────────────────────────────────────────

    private final OutputNormalizer normalizer = new OutputNormalizer();

    @Test
    @DisplayName("should normalize amounts to 2 decimal places")
    void shouldNormalizeAmounts() {
        // "1500" (no decimals) → "1500.00"
        ExtractedInvoice raw = new ExtractedInvoice(
                "INV-001", LocalDate.now(), new BigDecimal("1500"), "USD", null, List.of());

        ExtractedInvoice normalized = normalizer.normalize(raw);

        assertThat(normalized.amount()).isEqualByComparingTo("1500.00");
        assertThat(normalized.amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("should normalize currency to uppercase")
    void shouldNormalizeCurrency() {
        ExtractedInvoice raw = new ExtractedInvoice(
                "INV-001", LocalDate.now(), new BigDecimal("100.00"), " usd ", null, List.of());

        ExtractedInvoice normalized = normalizer.normalize(raw);

        assertThat(normalized.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("should trim and collapse whitespace in strings")
    void shouldNormalizeStrings() {
        ExtractedInvoice raw = new ExtractedInvoice(
                "  INV-001  ", LocalDate.now(), new BigDecimal("100.00"), "USD",
                "  john@example.com  ", List.of());

        ExtractedInvoice normalized = normalizer.normalize(raw);

        assertThat(normalized.invoiceNumber()).isEqualTo("INV-001");
        assertThat(normalized.customerEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("should make two format variants identical after normalization")
    void shouldUnifyFormatVariants() {
        // Simulate two different LLM responses for the same invoice:
        //   A: "  INV-2024-001", $1,500.00, "USD"
        //   B: "INV-2024-001 ", 1500, "usd"
        ExtractedInvoice responseA = new ExtractedInvoice(
                "  INV-2024-001", LocalDate.of(2024, 3, 21),
                new BigDecimal("1500.00"), "USD", null, List.of());
        ExtractedInvoice responseB = new ExtractedInvoice(
                "INV-2024-001 ", LocalDate.of(2024, 3, 21),
                new BigDecimal("1500"), "usd", null, List.of());

        ExtractedInvoice normA = normalizer.normalize(responseA);
        ExtractedInvoice normB = normalizer.normalize(responseB);

        assertThat(normA.invoiceNumber()).isEqualTo(normB.invoiceNumber());
        assertThat(normA.amount()).isEqualByComparingTo(normB.amount());
        assertThat(normA.currency()).isEqualTo(normB.currency());
        assertThat(normA.date()).isEqualTo(normB.date());

        // Both should normalize to the same values:
        // "INV-2024-001", 1500.00, "USD"
        assertThat(normA.invoiceNumber()).isEqualTo("INV-2024-001");
        assertThat(normA.amount().toPlainString()).isEqualTo("1500.00");
        assertThat(normA.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("should normalize line item unit prices")
    void shouldNormalizeLineItems() {
        ExtractedInvoice.LineItem item = new ExtractedInvoice.LineItem(
                "  Consulting Services  ", 2, new BigDecimal("500"));

        ExtractedInvoice raw = new ExtractedInvoice(
                "INV-001", LocalDate.now(), new BigDecimal("1000"), "USD", null, List.of(item));

        ExtractedInvoice normalized = normalizer.normalize(raw);

        assertThat(normalized.items()).hasSize(1);
        assertThat(normalized.items().getFirst().description()).isEqualTo("Consulting Services");
        assertThat(normalized.items().getFirst().unitPrice().scale()).isEqualTo(2);
    }
}
