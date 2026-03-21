package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.consensus.MultiModelConsensus.ConsensusResult;
import com.mehmandarov.llmvalidation.chapter5_consensus.ConsensusManager;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        ConsensusManager manager = new ConsensusManager(List.of(model1, model2, model3));

        // Act
        ConsensusResult result = manager.runConsensus(InvoiceTestData.MESSY_OCR);

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

        ConsensusManager manager = new ConsensusManager(List.of(model1, model2, model3));

        // Act
        ConsensusResult result = manager.runConsensus(InvoiceTestData.MESSY_OCR);

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
}
