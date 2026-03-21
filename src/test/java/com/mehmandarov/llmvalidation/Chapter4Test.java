package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter4_correction.CorrectiveExtractor;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Chapter 4: The Bargaining (Self-Correction)")
class Chapter4Test {

    private ChatModel mockModel;

    @BeforeEach
    void setup() {
        mockModel = Mockito.mock(ChatModel.class);
    }

    @Test
    @DisplayName("should correct invalid data through feedback loop")
    void shouldSelfCorrect() {
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
}
