package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter3_validation.InvoiceCalculatorTool;
import com.mehmandarov.llmvalidation.chapter3_validation.ExpressionEvaluator;
import com.mehmandarov.llmvalidation.chapter3_validation.OutputNormalizer;
import com.mehmandarov.llmvalidation.chapter3_validation.ToolAwareInvoiceExtractor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // ─────────────────────────────────────────────────────────────────────
    //  Function Calling / Tool Use — "Don't let the LLM do math"
    //  Instead of asking the LLM to compute totals or tax, we give it
    //  deterministic Java tools. The LLM decides WHEN to compute;
    //  the computation itself is pure code, zero hallucination risk.
    // ─────────────────────────────────────────────────────────────────────

    private final InvoiceCalculatorTool calculator = new InvoiceCalculatorTool();

    @Test
    @DisplayName("tool: calculateTotal sums line items deterministically")
    void toolShouldCalculateTotal() {
        List<BigDecimal> prices = List.of(
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("50.50"));

        BigDecimal total = calculator.calculateTotal(prices);

        // Pure Java math — always correct, unlike an LLM guess
        assertThat(total).isEqualByComparingTo("350.50");
    }

    @Test
    @DisplayName("tool: calculateTax computes tax deterministically")
    void toolShouldCalculateTax() {
        BigDecimal subtotal = new BigDecimal("1000.00");
        BigDecimal taxRate = new BigDecimal("21.0"); // 21% VAT

        BigDecimal tax = calculator.calculateTax(subtotal, taxRate);

        assertThat(tax).isEqualByComparingTo("210.00");
    }

    @Test
    @DisplayName("tool: calculateTotal handles empty list")
    void toolShouldHandleEmptyList() {
        BigDecimal total = calculator.calculateTotal(List.of());

        assertThat(total).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("tool-aware extractor can be wired with InvoiceCalculatorTool")
    void toolAwareExtractorCanBeWired() {
        // Arrange — mock model returns valid JSON (in a real scenario the model
        // would call calculateTotal via function calling, but we verify the wiring compiles)
        ChatModel model = mock(ChatModel.class);
        String json = """
            { "invoiceNumber": "INV-001", "date": "2024-03-21", "amount": 350.50, "currency": "USD" }
            """;
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
        when(model.chat(any(List.class))).thenReturn(response);
        when(model.chat(any(ChatRequest.class))).thenReturn(response);

        // Act — wire the tool into the extractor
        ToolAwareInvoiceExtractor extractor = AiServices.builder(ToolAwareInvoiceExtractor.class)
                .chatModel(model)
                .tools(new InvoiceCalculatorTool())
                .build();

        ExtractedInvoice result = extractor.extract("Items: Widget $100, Gadget $200, Cable $50.50");

        // Assert — the tool is available; in a live demo the model would call it
        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("350.50");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Code Execution — "Let the LLM write the formula, we execute it"
    //  The LLM generates an arithmetic expression (e.g. "750 + 250 + 125.50"),
    //  and we evaluate it deterministically. The generation is probabilistic,
    //  but the execution is not. And a wrong formula is easier to spot than
    //  a wrong number.
    // ─────────────────────────────────────────────────────────────────────

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    @Test
    @DisplayName("code exec: evaluates LLM-generated expression deterministically")
    void codeExecShouldEvaluateExpression() {
        // Imagine the LLM extracted line items and generated this formula:
        String llmFormula = "750 + 250 + 125.50";

        BigDecimal result = evaluator.evaluate(llmFormula);

        // Pure Java math — always 1125.50, even if the LLM generated the formula
        assertThat(result).isEqualByComparingTo("1125.50");
    }

    @Test
    @DisplayName("code exec: verifies LLM's claimed total matches the formula")
    void codeExecShouldVerifyClaimedTotal() {
        String formula = "100 + 200 + 50";
        BigDecimal correctClaim = new BigDecimal("350.00");
        BigDecimal wrongClaim = new BigDecimal("400.00");

        assertThat(evaluator.verify(formula, correctClaim)).isTrue();
        assertThat(evaluator.verify(formula, wrongClaim)).isFalse();
    }

    @Test
    @DisplayName("code exec: rejects unsafe expressions (no code injection)")
    void codeExecShouldRejectUnsafeInput() {
        // An attacker tries to sneak code into the expression
        assertThatThrownBy(() -> evaluator.evaluate("System.exit(0)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe characters");
    }

    @Test
    @DisplayName("code exec: handles operator precedence correctly")
    void codeExecShouldRespectPrecedence() {
        // 100 + 200 * 3 should be 700, not 900
        assertThat(evaluator.evaluate("100 + 200 * 3")).isEqualByComparingTo("700.00");
    }
}
