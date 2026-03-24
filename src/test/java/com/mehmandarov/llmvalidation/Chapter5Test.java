package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus.ConsensusResult;
import com.mehmandarov.llmvalidation.chapter5_consensus.StabilityAnalyzer;
import com.mehmandarov.llmvalidation.chapter5_consensus.StabilityAnalyzer.StabilityReport;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Chapter 5: The Council (Consensus)")
class Chapter5Test {

    @Test
    @DisplayName("should reach consensus when majority agrees")
    void shouldReachConsensus() {
        // Arrange
        String goodJson = """
            { "invoiceNumber": "INV-2024-001", "date": "2024-03-21", "amount": 1200.00, "currency": "USD" }
            """;
        String badJson = """
            { "invoiceNumber": "INV-2024-001", "date": "2024-03-21", "amount": 1000.00, "currency": "USD" }
            """;

        // 2 models agree (good), 1 disagrees (bad) — majority wins
        ChatModel model1 = mockModel("Gemma", goodJson);
        ChatModel model2 = mockModel("Llama", goodJson);
        ChatModel model3 = mockModel("Mistral", badJson);

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(model1, model2, model3));

        // Act
        ConsensusResult result = consensus.runConsensus(InvoiceTestData.MESSY_OCR);

        // Assert
        assertThat(result.isHighConfidence()).isTrue();
        assertThat(result.consensus().invoiceNumber()).isEqualTo("INV-2024-001");
        assertThat(result.consensus().amount()).isEqualByComparingTo("1200.00"); // majority amount
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("should flag low confidence when models disagree")
    void shouldFlagLowConfidence() {
        // Arrange — all 3 models return different amounts
        ChatModel model1 = mockModel("Gemma", """
            { "invoiceNumber": "INV-001", "date": "2024-03-21", "amount": 100.00, "currency": "USD" }
            """);
        ChatModel model2 = mockModel("Llama", """
            { "invoiceNumber": "INV-001", "date": "2024-03-21", "amount": 200.00, "currency": "USD" }
            """);
        ChatModel model3 = mockModel("Mistral", """
            { "invoiceNumber": "INV-001", "date": "2024-03-21", "amount": 300.00, "currency": "USD" }
            """);

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(model1, model2, model3));

        // Act
        ConsensusResult result = consensus.runConsensus(InvoiceTestData.MESSY_OCR);

        // Assert — no majority, confidence is only 1/3 ≈ 0.33
        assertThat(result.isHighConfidence()).isFalse();
        assertThat(result.confidence()).isLessThan(0.6);
    }

    private ChatModel mockModel(String name, String json) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
        when(model.chat(any(List.class))).thenReturn(response);
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
        when(model.toString()).thenReturn(name);
        return model;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  StabilityAnalyzer tests
    // ─────────────────────────────────────────────────────────────────────

    private final StabilityAnalyzer stabilityAnalyzer = new StabilityAnalyzer();

    @Test
    @DisplayName("should report 100% stability when all results are identical")
    void shouldReportPerfectStability() {
        ExtractedInvoice same = new ExtractedInvoice(
                "INV-001", LocalDate.of(2024, 3, 21),
                new BigDecimal("1500.00"), "USD", null, List.of());

        StabilityReport report = stabilityAnalyzer.analyze(List.of(same, same, same, same, same));

        assertThat(report.overallStability()).isEqualTo(1.0);
        assertThat(report.fieldReports().get("invoiceNumber").agreement()).isEqualTo(1.0);
        assertThat(report.fieldReports().get("amount").agreement()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should report low stability when results disagree")
    void shouldReportLowStability() {
        // 5 runs, all different amounts — no majority
        List<ExtractedInvoice> chaotic = List.of(
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("100"), "USD", null, List.of()),
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("200"), "USD", null, List.of()),
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("300"), "USD", null, List.of()),
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("400"), "USD", null, List.of()),
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("500"), "USD", null, List.of())
        );

        StabilityReport report = stabilityAnalyzer.analyze(chaotic);

        // invoiceNumber, date, currency all agree → 1.0 each
        // amount: 5 different values, each 1/5 → 0.2
        // overall: (1.0 + 1.0 + 0.2 + 1.0) / 4 = 0.8
        assertThat(report.fieldReports().get("amount").agreement()).isEqualTo(0.2);
        assertThat(report.overallStability()).isLessThan(1.0);
    }

    @Test
    @DisplayName("should identify the dominant value per field")
    void shouldIdentifyDominantValues() {
        // 3 say "INV-001", 2 say "INV-002"
        List<ExtractedInvoice> results = List.of(
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("1500"), "USD", null, List.of()),
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("1500"), "USD", null, List.of()),
                new ExtractedInvoice("INV-001", LocalDate.of(2024, 3, 21), new BigDecimal("1500"), "USD", null, List.of()),
                new ExtractedInvoice("INV-002", LocalDate.of(2024, 3, 21), new BigDecimal("1500"), "USD", null, List.of()),
                new ExtractedInvoice("INV-002", LocalDate.of(2024, 3, 21), new BigDecimal("1500"), "USD", null, List.of())
        );

        StabilityReport report = stabilityAnalyzer.analyze(results);

        assertThat(report.fieldReports().get("invoiceNumber").dominantValue()).isEqualTo("INV-001");
        assertThat(report.fieldReports().get("invoiceNumber").agreement()).isEqualTo(0.6);
    }
}
