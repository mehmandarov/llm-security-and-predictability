package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PiiGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.SecureInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter4_correction.CorrectiveExtractor;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus.ConsensusResult;
import com.mehmandarov.llmvalidation.chapter5_consensus.StabilityAnalyzer;
import com.mehmandarov.llmvalidation.chapter5_consensus.StabilityAnalyzer.StabilityReport;
import com.mehmandarov.llmvalidation.chapter6_bonus_mirror.MirrorVerifier;
import com.mehmandarov.llmvalidation.chapter6_bonus_mirror.MirrorVerifier.VerificationResult;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.guardrail.InputGuardrailException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The Narrative Test Suite.
 * Runs through the 5 chapters of the talk.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LLMValidationTalkTest {

    private static ChatModel mockModel;

    @BeforeAll
    static void setup() {
        mockModel = Mockito.mock(ChatModel.class);
    }

    // --- Chapter 1: The Basics ---
    @Test
    @Order(1)
    void chapter1_TheHoneymoon_shouldExtractCleanInvoice() {
        // Arrange
        String jsonResponse = """
            { "invoiceNumber": "INV-2024-001", "date": "2024-03-21", "amount": 1500.00, "currency": "USD" }
            """;
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(jsonResponse)).build();
        when(mockModel.chat(any(List.class))).thenReturn(response);
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(response);
        
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, mockModel);

        // Act
        ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);

        // Assert
        assertThat(result.invoiceNumber()).isEqualTo("INV-2024-001");
        assertThat(result.amount()).isEqualByComparingTo("1500.00");
    }

    // --- Chapter 2: The Attack (Security) ---
    @Test
    @Order(2)
    void chapter2_TheAttack_shouldBlockInjection() {
        // Arrange
        SecureInvoiceExtractor extractor = AiServices.create(SecureInvoiceExtractor.class, mockModel);

        // Act & Assert
        // InputGuardrailException is thrown BEFORE the model is called
        assertThatThrownBy(() -> extractor.extract(InvoiceTestData.INJECTION_ATTACK))
            .isInstanceOf(InputGuardrailException.class)
            .hasMessageContaining("Security Violation");
    }

    @Test
    @Order(3)
    void chapter2_TheAttack_shouldRedactPii() {
        // Arrange — a response containing PII that should be scrubbed
        String leakedResponse = """
            {
              "invoiceNumber": "INV-PRIVACY-001",
              "date": "2024-03-21",
              "amount": 500.00,
              "currency": "USD",
              "customerEmail": "private.john@example.com"
            }
            """;

        // Act — run the guardrail directly to verify redaction
        PiiGuardrail guardrail = new PiiGuardrail();
        AiMessage responseWithPii = AiMessage.from(leakedResponse);
        var result = guardrail.validate(responseWithPii);

        // Assert — email is replaced with [REDACTED_EMAIL]
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.successfulText()).contains("[REDACTED_EMAIL]");
        assertThat(result.successfulText()).doesNotContain("private.john@example.com");
    }

    // --- Chapter 3: The Hallucination (Validation) ---
    @Test
    @Order(4)
    void chapter3_TheHallucination_shouldCatchFutureDate() {
        // Arrange
        ExtractedInvoice hallucinatedInvoice = new ExtractedInvoice(
            "INV-FUTURE-001", 
            LocalDate.of(2050, 1, 1), 
            new BigDecimal("1000000.00"), 
            "USD", 
            null,
            List.of()
        );
        
        StrictValidator validator = new StrictValidator();

        // Act
        ValidationResult result = validator.validate(hallucinatedInvoice);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.message().contains("future"));
    }

    @Test
    @Order(5)
    void chapter3_TheHallucination_shouldCatchMathError() {
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
        
        StrictValidator validator = new StrictValidator();

        // Act
        ValidationResult result = validator.validate(badMathInvoice);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.category().equals("BUSINESS"));
    }

    // --- Chapter 4: The Bargaining (Correction) ---
    @Test
    @Order(6)
    void chapter4_TheBargaining_shouldSelfCorrect() {
        // Arrange
        String wrongJson = """
            { "invoiceNumber": "INV-001", "date": "2050-01-01", "amount": 100.00, "currency": "USD" }
            """;
        String correctedJson = """
            { "invoiceNumber": "INV-001", "date": "2026-01-15", "amount": 100.00, "currency": "USD" }
            """;
            
        ChatResponse wrongResponse = ChatResponse.builder().aiMessage(AiMessage.from(wrongJson)).build();
        ChatResponse correctedResponse = ChatResponse.builder().aiMessage(AiMessage.from(correctedJson)).build();

        // First call returns wrong JSON, Second call (triggered by feedback loop) returns correct JSON
        when(mockModel.chat(any(List.class)))
            .thenReturn(wrongResponse)
            .thenReturn(correctedResponse);
        when(mockModel.chat(any(ChatRequest.class)))
            .thenReturn(wrongResponse)
            .thenReturn(correctedResponse);
            
        SimpleInvoiceExtractor basicExtractor = AiServices.create(SimpleInvoiceExtractor.class, mockModel);
        StrictValidator validator = new StrictValidator();
        CorrectiveExtractor corrective = new CorrectiveExtractor(basicExtractor, validator, 3);

        // Act
        ExtractedInvoice result = corrective.extract(InvoiceTestData.FUTURE_DATE_HALLUCINATION);

        // Assert
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 1, 15)); // The corrected date
    }

    // --- Chapter 5: The Council (Consensus) ---
    @Test
    @Order(7)
    void chapter5_TheCouncil_shouldReachConsensus() {
        // Arrange — 2 models agree on $1200, 1 model says $1000
        String majorityJson = """
            { "invoiceNumber": "INV-2024-001", "date": "2024-03-21", "amount": 1200.00, "currency": "USD" }
            """;
        String outlierJson = """
            { "invoiceNumber": "INV-2024-001", "date": "2024-03-21", "amount": 1000.00, "currency": "USD" }
            """;

        ChatModel model1 = mockChatModel(majorityJson);
        ChatModel model2 = mockChatModel(majorityJson);
        ChatModel model3 = mockChatModel(outlierJson);

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(model1, model2, model3));

        // Act
        ConsensusResult result = consensus.runConsensus(InvoiceTestData.MESSY_OCR);

        // Assert — majority wins
        assertThat(result.isHighConfidence()).isTrue();
        assertThat(result.consensus().amount()).isEqualByComparingTo("1200.00");
    }

    // --- Chapter 5 continued: Stability Analysis ---
    @Test
    @Order(8)
    void chapter5_TheCouncil_shouldMeasureStability() {
        // Arrange — 5 identical results = perfect stability
        ExtractedInvoice same = new ExtractedInvoice(
                "INV-2024-001", LocalDate.of(2024, 3, 21),
                new BigDecimal("1500.00"), "USD", null, List.of());

        StabilityAnalyzer analyzer = new StabilityAnalyzer();

        // Act
        StabilityReport report = analyzer.analyze(List.of(same, same, same, same, same));

        // Assert
        assertThat(report.overallStability()).isEqualTo(1.0);
        assertThat(report.fieldReports().get("amount").agreement()).isEqualTo(1.0);
    }

    // --- Bonus: The Mirror Test ---
    @Test
    @Order(9)
    void bonus_TheMirrorTest_shouldDetectOmissions() {
        // Arrange — model "forgets" the Mouse item
        String syntheticSummary = "Invoice INV-001 for $1000.00 containing a Laptop.";
        ChatResponse reconstructionResp = ChatResponse.builder()
                .aiMessage(AiMessage.from(syntheticSummary)).build();
        ChatResponse verificationResp = ChatResponse.builder()
                .aiMessage(AiMessage.from("0.6")).build();

        when(mockModel.chat(any(List.class)))
                .thenReturn(reconstructionResp)
                .thenReturn(verificationResp);
        when(mockModel.chat(any(ChatRequest.class)))
                .thenReturn(reconstructionResp)
                .thenReturn(verificationResp);

        MirrorVerifier verifier = new MirrorVerifier(mockModel);
        String originalText = "Invoice INV-001. Items: 1x Laptop ($1000), 1x Mouse ($50). Total: $1050.";
        String extractedJson = "{ \"invoiceNumber\": \"INV-001\", \"amount\": 1000.00 }";

        // Act
        VerificationResult result = verifier.verify(originalText, extractedJson);

        // Assert — low score because the Mouse was omitted
        assertThat(result.faithfulnessScore()).isLessThan(0.8);
    }

    private ChatModel mockChatModel(String json) {
        ChatModel model = Mockito.mock(ChatModel.class);
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
        when(model.chat(any(List.class))).thenReturn(response);
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
        return model;
    }
}
