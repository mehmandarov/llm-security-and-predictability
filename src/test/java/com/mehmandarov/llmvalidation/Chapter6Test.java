package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter6_bonus_mirror.MirrorVerifier;
import com.mehmandarov.llmvalidation.chapter6_bonus_mirror.MirrorVerifier.VerificationResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Bonus: The Mirror Test (Synthetic Verification)")
class Chapter6Test {

    @Test
    @DisplayName("should catch omissions via synthetic reconstruction")
    void shouldCatchOmissions() {
        // Arrange
        ChatModel mockModel = Mockito.mock(ChatModel.class);
        MirrorVerifier verifier = new MirrorVerifier(mockModel);

        String originalText = "Invoice INV-001. Items: 1x Laptop ($1000), 1x Mouse ($50). Total: $1050.";
        
        // Simulation: The LLM extraction forgot the Mouse!
        String extractedJson = "{ \"invoiceNumber\": \"INV-001\", \"amount\": 1000.00, \"items\": [{\"description\": \"Laptop\"}] }";

        // 1. Mock the Reconstruction phase (Step 2)
        // The reconstructor reads the JSON and writes a summary.
        String syntheticSummary = "Invoice INV-001 for $1000.00 containing a Laptop.";
        ChatResponse reconstructionResp = ChatResponse.builder().aiMessage(AiMessage.from(syntheticSummary)).build();
        
        // 2. Mock the Verification phase (Step 3)
        // The checker compares original vs summary and sees the missing item ($50 diff).
        ChatResponse verificationResp = ChatResponse.builder().aiMessage(AiMessage.from("0.6")).build();

        when(mockModel.chat(any(List.class)))
                .thenReturn(reconstructionResp) // First call: reconstruct
                .thenReturn(verificationResp);   // Second call: check faithfulness
        
        // Mocking ChatRequest overload too
        when(mockModel.chat(any(ChatRequest.class)))
                .thenReturn(reconstructionResp)
                .thenReturn(verificationResp);

        // Act
        VerificationResult result = verifier.verify(originalText, extractedJson);

        // Assert
        assertThat(result.faithfulnessScore()).isLessThan(0.8);
        assertThat(result.syntheticSummary()).contains("Laptop");
        assertThat(result.syntheticSummary()).doesNotContain("Mouse");
    }

    @Test
    @DisplayName("should verify faithful extractions")
    void shouldVerifyFaithfulExtractions() {
        // Arrange
        ChatModel mockModel = Mockito.mock(ChatModel.class);
        MirrorVerifier verifier = new MirrorVerifier(mockModel);

        String originalText = "Invoice INV-001 for $100.";
        String extractedJson = "{ \"invoiceNumber\": \"INV-001\", \"amount\": 100.00 }";

        ChatResponse reconstructionResp = ChatResponse.builder().aiMessage(AiMessage.from("Summary: INV-001, $100.")).build();
        ChatResponse verificationResp = ChatResponse.builder().aiMessage(AiMessage.from("1.0")).build();

        when(mockModel.chat(any(List.class)))
                .thenReturn(reconstructionResp)
                .thenReturn(verificationResp);
        
        when(mockModel.chat(any(ChatRequest.class)))
                .thenReturn(reconstructionResp)
                .thenReturn(verificationResp);

        // Act
        VerificationResult result = verifier.verify(originalText, extractedJson);

        // Assert
        assertThat(result.faithfulnessScore()).isEqualTo(1.0);
    }
}

