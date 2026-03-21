package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Chapter 1: The Honeymoon (Naive Implementation)")
class Chapter1Test {

    private ChatModel mockModel;

    @BeforeEach
    void setup() {
        mockModel = Mockito.mock(ChatModel.class);
    }

    @Test
    @DisplayName("should extract clean invoice successfully")
    void shouldExtractCleanInvoice() {
        // Arrange
        String jsonResponse = """
            { "invoiceNumber": "INV-2024-001", "date": "2024-03-21", "amount": 1500.00, "currency": "USD" }
            """;
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(jsonResponse)).build();
        // Mocking both chat(List) and chat(ChatRequest) to cover all bases
        when(mockModel.chat(any(List.class))).thenReturn(response);
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(response);
        
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, mockModel);

        // Act
        ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);

        // Assert
        assertThat(result.invoiceNumber()).isEqualTo("INV-2024-001");
        assertThat(result.amount()).isEqualByComparingTo("1500.00");
    }
}
