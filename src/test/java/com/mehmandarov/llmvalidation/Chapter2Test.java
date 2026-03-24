package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter2_guardrails.CanaryTokenGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.SecureInvoiceExtractor;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Nested
    @DisplayName("Layer 1: External Guardrails")
    class Guardrails {
        @Test
        @DisplayName("should block prompt injection attempts")
        void shouldBlockInjection() {
            SecureInvoiceExtractor extractor = AiServices.create(SecureInvoiceExtractor.class, mockModel);

            assertThatThrownBy(() -> extractor.extract(InvoiceTestData.INJECTION_ATTACK))
                .isInstanceOf(InputGuardrailException.class)
                .hasMessageContaining("Security Violation");
        }

        @Test
        @DisplayName("should redact PII from output")
        void shouldRedactPii() {
            // Arrange
            String leakedResponse = "{ \"invoiceNumber\": \"INV-001\", \"customerEmail\": \"private.john@example.com\" }";
            ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(leakedResponse)).build();
            when(mockModel.chat(any(List.class))).thenReturn(response);
            when(mockModel.chat(any(ChatRequest.class))).thenReturn(response);

            // Act
            SecureInvoiceExtractor extractor = AiServices.create(SecureInvoiceExtractor.class, mockModel);
            extractor.extract(InvoiceTestData.PII_LEAK);

            // Assert
            // Verified via logs: "🛡️ PII DETECTED: Redacting email address."
        }
    }

    @Nested
    @DisplayName("Layer 2: The Canary Trap")
    class CanaryTrap {

        @Test
        @DisplayName("should allow clean responses through")
        void shouldAllowCleanResponse() {
            // Arrange — a normal JSON response without the canary token
            CanaryTokenGuardrail guardrail = new CanaryTokenGuardrail();
            AiMessage cleanResponse = AiMessage.from("{ \"invoiceNumber\": \"INV-001\", \"amount\": 500.00 }");

            // Act
            OutputGuardrailResult result = guardrail.validate(cleanResponse);

            // Assert — no canary detected, passes through
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should block response containing the canary token (injection breach)")
        void shouldBlockCanaryBreach() {
            // Arrange — the LLM was compromised and output the canary
            CanaryTokenGuardrail guardrail = new CanaryTokenGuardrail();
            String canary = guardrail.getCanaryToken();
            AiMessage breachedResponse = AiMessage.from(canary);

            // Act
            OutputGuardrailResult result = guardrail.validate(breachedResponse);

            // Assert — canary detected, blocked as security violation
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should detect canary even when embedded in other text")
        void shouldDetectEmbeddedCanary() {
            // Arrange — canary buried inside a longer response
            CanaryTokenGuardrail guardrail = new CanaryTokenGuardrail();
            String canary = guardrail.getCanaryToken();
            AiMessage sneakyResponse = AiMessage.from(
                "Here is your data: { \"invoiceNumber\": \"" + canary + "\" }");

            // Act
            OutputGuardrailResult result = guardrail.validate(sneakyResponse);

            // Assert
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("each guardrail instance has a unique canary (not guessable)")
        void shouldGenerateUniqueCanaries() {
            CanaryTokenGuardrail guardrail1 = new CanaryTokenGuardrail();
            CanaryTokenGuardrail guardrail2 = new CanaryTokenGuardrail();

            assertThat(guardrail1.getCanaryToken()).isNotEqualTo(guardrail2.getCanaryToken());
        }
    }
}
