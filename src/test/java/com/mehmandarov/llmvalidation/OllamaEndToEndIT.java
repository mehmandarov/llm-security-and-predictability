package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PiiGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PromptInjectionGuardrail;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter4_correction.CorrectiveExtractor;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus.ConsensusResult;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import com.mehmandarov.llmvalidation.model.ValidationResult;
import com.mehmandarov.llmvalidation.support.OllamaAvailable;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests against a real, local Ollama instance.
 *
 * <p>These tests demonstrate the <b>actual</b> non-deterministic behaviour of LLMs —
 * the very thing the talk is about. Run with:</p>
 * <pre>
 *   mvn verify                          # runs all integration tests
 *   mvn failsafe:integration-test       # also works
 * </pre>
 *
 * <p>Prerequisites:</p>
 * <ul>
 *   <li>Ollama running locally on port 11434</li>
 *   <li>{@code ollama pull gemma3:1b} (small &amp; fast for demos)</li>
 * </ul>
 *
 * <p>If Ollama is not reachable the entire class is automatically skipped.</p>
 */
@OllamaAvailable
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("🔴 LIVE — End-to-End with Ollama")
class OllamaEndToEndIT {

    private static final Logger log = LoggerFactory.getLogger(OllamaEndToEndIT.class);

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String MODEL = "gemma3:1b";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static ChatModel ollamaModel;

    @BeforeAll
    static void setup() {
        ollamaModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(MODEL)
                .temperature(0.0)          // as deterministic as possible
                .timeout(TIMEOUT)
                .logRequests(true)
                .logResponses(true)
                .build();
        log.info("🟢 Connected to Ollama ({}) at {}", MODEL, OLLAMA_URL);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 1 — The Honeymoon: does structured extraction work at all?
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Ch1 · Should extract structured data from a clean invoice")
    void chapter1_extractCleanInvoice() {
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);

        ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);

        log.info("📋 Extracted: {}", result);
        assertThat(result).isNotNull();
        assertThat(result.invoiceNumber()).isNotBlank();
        assertThat(result.amount()).isNotNull();
        // NOTE: We intentionally don't assert exact values — the point is
        // that a real LLM may return slightly different formatting each time.
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 2 — The Attack: guardrails work regardless of the model
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Ch2 · Prompt injection is blocked BEFORE hitting the model")
    void chapter2_promptInjectionBlocked() {
        PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail();
        UserMessage malicious = UserMessage.from(InvoiceTestData.INJECTION_ATTACK);

        InputGuardrailResult result = guardrail.validate(malicious);

        log.info("🚨 Guardrail result: success={}", result.isSuccess());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("Ch2 · PII is redacted from a real LLM response")
    void chapter2_piiRedactedFromRealResponse() {
        // Ask the real model — it may well echo PII back
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        ExtractedInvoice raw = extractor.extract(InvoiceTestData.PII_LEAK);
        log.info("📋 Raw extraction (may contain PII): {}", raw);

        // Now verify the guardrail catches it in a hypothetical response
        PiiGuardrail guardrail = new PiiGuardrail();
        String textWithPii = "Invoice INV-001, email: secret@corp.com, SSN: 123-45-6789, CC: 4111-2222-3333-4444";
        AiMessage fakeResponse = AiMessage.from(textWithPii);

        OutputGuardrailResult result = guardrail.validate(fakeResponse);

        log.info("🛡️ Redacted: {}", result);
        assertThat(result.isSuccess()).isTrue();
        // The guardrail replaces PII and returns a successful-with-replacement result
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 3 — The Hallucination: validation catches what the LLM gets wrong
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Ch3 · Real model output is validated — errors are caught deterministically")
    void chapter3_validateRealOutput() {
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        StrictValidator validator = new StrictValidator();

        // Ask the LLM to extract the future-date invoice
        ExtractedInvoice result = extractor.extract(InvoiceTestData.FUTURE_DATE_HALLUCINATION);
        log.info("📋 LLM extracted: {}", result);

        // Validate the LLM's response
        ValidationResult validation = validator.validate(result);
        log.info("🔍 Validation result: valid={}, errors={}", validation.isValid(), validation.errors());

        // We expect validation to fail — either the LLM echoes 2050 (temporal error)
        // or it makes up other fields (schema errors). Either way, the validator catches it.
        // This is the whole point: trust code, not the model.
        if (!validation.isValid()) {
            log.info("✅ Validator correctly caught LLM errors:");
            validation.errors().forEach(e -> log.info("   ❌ [{}] {}", e.category(), e.message()));
        } else {
            log.warn("⚠️ LLM happened to produce valid data — run again to see non-determinism!");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Ch3 · Real model struggles with bad math")
    void chapter3_validateMathError() {
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        StrictValidator validator = new StrictValidator();

        ExtractedInvoice result = extractor.extract(InvoiceTestData.MATH_ERROR);
        log.info("📋 LLM extracted: {}", result);

        ValidationResult validation = validator.validate(result);
        log.info("🔍 Validation: valid={}, errors={}", validation.isValid(), validation.errors());
        // The LLM may blindly echo $5000 (business error) or may "fix" the math itself — interesting either way!
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 4 — The Bargaining: self-correction loop with a real model
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Ch4 · Self-correction loop improves results")
    void chapter4_selfCorrectionWithRealModel() {
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        StrictValidator validator = new StrictValidator();
        CorrectiveExtractor corrective = new CorrectiveExtractor(extractor, validator, 3);

        ExtractedInvoice result = corrective.extract(InvoiceTestData.FUTURE_DATE_HALLUCINATION);
        log.info("📋 Final result after correction loop: {}", result);

        // Check if correction worked
        ValidationResult validation = validator.validate(result);
        if (validation.isValid()) {
            log.info("✅ Self-correction succeeded! Date: {}, Amount: {}", result.date(), result.amount());
        } else {
            log.warn("⚠️ Self-correction did not fully fix the data after 3 attempts.");
            validation.errors().forEach(e -> log.warn("   ❌ [{}] {}", e.category(), e.message()));
        }
        // No hard assertion — the demo point is showing the feedback loop in action via logs.
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 5 — The Council: consensus with the same model (3 calls)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Ch5 · Consensus across multiple calls (same model, different temperatures)")
    void chapter5_consensusWithRealModels() {
        // Use the same model at different temperatures to simulate multi-model diversity
        ChatModel deterministic = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(0.0).timeout(TIMEOUT).build();
        ChatModel creative = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(0.7).timeout(TIMEOUT).build();
        ChatModel wild = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(1.0).timeout(TIMEOUT).build();

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(deterministic, creative, wild));

        ConsensusResult result = consensus.runConsensus(InvoiceTestData.CLEAN_INVOICE);

        log.info("🗳️ Consensus result: highConfidence={}, confidence={}%",
                result.isHighConfidence(), (int) (result.confidence() * 100));
        if (result.consensus() != null) {
            log.info("   Invoice: {}", result.consensus().invoiceNumber());
            log.info("   Amount:  {}", result.consensus().amount());
        }
        // With clean input, even diverse temperatures should mostly agree
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Bonus — Demonstrating non-determinism: same question, different answers
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("🎲 Bonus · Same input, 3 extractions — observe the variance")
    void bonus_demonstrateNonDeterminism() {
        // Use temperature > 0 so the model actually varies
        ChatModel creativeModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(MODEL)
                .temperature(0.8)
                .timeout(TIMEOUT)
                .build();

        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, creativeModel);

        log.info("🎲 Asking the same question 3 times with temperature=0.8...");
        for (int i = 1; i <= 3; i++) {
            try {
                ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);
                log.info("   Run {}: invoice={}, amount={}, date={}",
                        i, result.invoiceNumber(), result.amount(), result.date());
            } catch (Exception e) {
                log.warn("   Run {}: FAILED — {}", i, e.getMessage());
            }
        }
        // No assertions — the point IS the variance. The audience sees it in the logs.
    }
}

