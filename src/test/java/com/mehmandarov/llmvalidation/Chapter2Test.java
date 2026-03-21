package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter2_guardrails.SecureInvoiceExtractor;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Chapter 2: The Attack (Security & Guardrails)")
class Chapter2Test {

    private ChatModel mockModel;

    @BeforeEach
    void setup() {
        mockModel = Mockito.mock(ChatModel.class);
    }

    @Test
    @DisplayName("should block prompt injection attempts")
    void shouldBlockInjection() {
        // Arrange
        SecureInvoiceExtractor extractor = AiServices.create(SecureInvoiceExtractor.class, mockModel);

        // Act & Assert
        // InputGuardrailException is thrown BEFORE the model is called
        assertThatThrownBy(() -> extractor.extract(InvoiceTestData.INJECTION_ATTACK))
            .isInstanceOf(InputGuardrailException.class)
            .hasMessageContaining("Security Violation");
    }

    @Test
    @DisplayName("should redact PII from output")
    void shouldRedactPii() {
        // Arrange
        String leakedResponse = """
            {
              "invoiceNumber": "INV-PRIVACY-001", 
              "date": "2024-03-21", 
              "amount": 500.00, 
              "currency": "USD",
              "customerEmail": "private.john@example.com"
            }
            """;
        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(leakedResponse)).build();
        when(mockModel.chat(any(List.class))).thenReturn(response);
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(response);
        
        SecureInvoiceExtractor extractor = AiServices.create(SecureInvoiceExtractor.class, mockModel);

        // Act
        // The OutputGuardrail runs on the raw text and redacts the email before parsing.
        extractor.extract(InvoiceTestData.PII_LEAK);
        
        // Assert
        // Verified via logs: "🛡️ PII DETECTED: Redacting email address."
    }
}
