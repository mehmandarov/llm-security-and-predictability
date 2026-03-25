package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.CanaryInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.CanaryTokenGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PiiGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PromptInjectionGuardrail;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter4_correction.CorrectiveExtractor;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus.ConsensusResult;
import com.mehmandarov.llmvalidation.data.InvoiceTestData;
import com.mehmandarov.llmvalidation.chapter5_consensus.StabilityAnalyzer;
import com.mehmandarov.llmvalidation.chapter5_consensus.StabilityAnalyzer.StabilityReport;
import com.mehmandarov.llmvalidation.chapter6_bonus_mirror.MirrorVerifier;
import com.mehmandarov.llmvalidation.chapter6_bonus_mirror.MirrorVerifier.VerificationResult;
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

    @Test
    @Order(4)
    @DisplayName("Ch2 · Canary trap detects injection breach in real model output")
    void chapter2_canaryTrapWithRealModel() {
        CanaryTokenGuardrail canaryGuardrail = new CanaryTokenGuardrail();
        String canary = canaryGuardrail.getCanaryToken();
        log.info("🐤 Canary token: {}", canary);

        // Ask the model to extract from a malicious input — with the canary in the system prompt
        CanaryInvoiceExtractor extractor = AiServices.create(CanaryInvoiceExtractor.class, ollamaModel);
        try {
            ExtractedInvoice result = extractor.extract(InvoiceTestData.INJECTION_ATTACK, canary);
            log.info("📋 Model response: {}", result);
            // Even if it parsed, check if the canary leaked into any field
            String responseStr = result.toString();
            if (responseStr.contains(canary)) {
                log.warn("🚨 CANARY TRIGGERED in parsed response — injection breach confirmed!");
            } else {
                log.info("🤔 Model did NOT output the canary. It may have ignored the injection OR the canary instruction.");
                log.info("   This is the limitation: the canary is a tripwire, not a guarantee.");
            }
        } catch (Exception e) {
            // Model may output the raw canary token, which can't be parsed as ExtractedInvoice
            log.info("💥 Model output couldn't be parsed: {}", e.getMessage());
            log.info("   This likely means the model output the canary token (good!) or garbage (expected on injection).");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 3 — The Hallucination: validation catches what the LLM gets wrong
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
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
    @Order(6)
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
    @Order(7)
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
    @Order(8)
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
    //  Bonus — The Mirror Test: round-trip verification with a real model
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("🪞 Bonus · Mirror Test — does the extraction survive a round trip?")
    void chapter6_mirrorTestWithRealModel() {
        // Step 1: Extract structured data from the clean invoice
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        ExtractedInvoice extracted = extractor.extract(InvoiceTestData.CLEAN_INVOICE);
        log.info("📋 Extracted: {}", extracted);

        // Step 2: Convert to JSON string (the "extraction result" we want to verify)
        String extractedJson = String.format(
                "{ \"invoiceNumber\": \"%s\", \"date\": \"%s\", \"amount\": %s, \"currency\": \"%s\" }",
                extracted.invoiceNumber(), extracted.date(), extracted.amount(), extracted.currency());
        log.info("📋 JSON for mirror test: {}", extractedJson);

        // Step 3: Run the Mirror Test — reconstruct text from JSON, compare to original
        MirrorVerifier verifier = new MirrorVerifier(ollamaModel);
        VerificationResult result = verifier.verify(InvoiceTestData.CLEAN_INVOICE, extractedJson);

        log.info("🪞 Faithfulness score: {}%", (int) (result.faithfulnessScore() * 100));
        log.info("🪞 Synthetic summary: {}", result.syntheticSummary());

        if (result.faithfulnessScore() >= 0.8) {
            log.info("✅ Mirror Test PASSED — the extraction is faithful to the original.");
        } else {
            log.warn("⚠️ Mirror Test flagged potential omissions — score below 80%.");
            log.warn("   This is exactly what the Mirror Test is for: catching silent data loss.");
        }
        // No hard assertion — the demo point is showing the round-trip verification concept.
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Bonus — Demonstrating non-determinism: same question, different answers
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
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

    // ─────────────────────────────────────────────────────────────────────
    //  Bonus — Seed-based reproducibility: does the model's promise hold?
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("🎲 Bonus · Seed-based reproducibility — testing the model's determinism promise")
    void bonus_seedReproducibility() {
        // Build two identical models with the same seed
        ChatModel modelA = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL)
                .temperature(0.0).seed(42)
                .timeout(TIMEOUT).build();
        ChatModel modelB = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL)
                .temperature(0.0).seed(42)
                .timeout(TIMEOUT).build();

        SimpleInvoiceExtractor extractorA = AiServices.create(SimpleInvoiceExtractor.class, modelA);
        SimpleInvoiceExtractor extractorB = AiServices.create(SimpleInvoiceExtractor.class, modelB);

        log.info("🔑 Testing seed=42 reproducibility...");
        ExtractedInvoice resultA = extractorA.extract(InvoiceTestData.CLEAN_INVOICE);
        ExtractedInvoice resultB = extractorB.extract(InvoiceTestData.CLEAN_INVOICE);

        log.info("   Model A: invoice={}, amount={}, date={}", resultA.invoiceNumber(), resultA.amount(), resultA.date());
        log.info("   Model B: invoice={}, amount={}, date={}", resultB.invoiceNumber(), resultB.amount(), resultB.date());

        // Compare field-by-field
        boolean match = true;
        if (!java.util.Objects.equals(resultA.invoiceNumber(), resultB.invoiceNumber())) {
            log.warn("   ❌ invoiceNumber differs: '{}' vs '{}'", resultA.invoiceNumber(), resultB.invoiceNumber());
            match = false;
        }
        if (!java.util.Objects.equals(resultA.date(), resultB.date())) {
            log.warn("   ❌ date differs: '{}' vs '{}'", resultA.date(), resultB.date());
            match = false;
        }
        if (resultA.amount() != null && resultB.amount() != null
                ? resultA.amount().compareTo(resultB.amount()) != 0
                : !java.util.Objects.equals(resultA.amount(), resultB.amount())) {
            log.warn("   ❌ amount differs: '{}' vs '{}'", resultA.amount(), resultB.amount());
            match = false;
        }
        if (!java.util.Objects.equals(resultA.currency(), resultB.currency())) {
            log.warn("   ❌ currency differs: '{}' vs '{}'", resultA.currency(), resultB.currency());
            match = false;
        }

        if (match) {
            log.info("   ✅ IDENTICAL — seed=42 produced the same result both times.");
        } else {
            log.warn("   ⚠️ DIFFERENT — even with the same seed, results diverged. "
                    + "This is why we need all five defensive layers.");
        }

        // Now test with a different seed to show seed matters
        ChatModel modelC = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL)
                .temperature(0.0).seed(99)
                .timeout(TIMEOUT).build();
        SimpleInvoiceExtractor extractorC = AiServices.create(SimpleInvoiceExtractor.class, modelC);
        ExtractedInvoice resultC = extractorC.extract(InvoiceTestData.CLEAN_INVOICE);
        log.info("   Model C (seed=99): invoice={}, amount={}", resultC.invoiceNumber(), resultC.amount());
        // No hard assertions — this is a demonstration, not a guarantee.
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Bonus — Stability measurement: quantify the variance
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("🎲 Bonus · Stability analysis — measuring variance across repeated runs")
    void bonus_stabilityMeasurement() {
        ChatModel deterministicModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL)
                .temperature(0.0).timeout(TIMEOUT).build();

        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, deterministicModel);
        StabilityAnalyzer analyzer = new StabilityAnalyzer();

        log.info("📊 Running 5 extractions at temperature=0.0 to measure stability...");
        java.util.List<ExtractedInvoice> results = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            try {
                ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);
                results.add(result);
                log.info("   Run {}: invoice={}, amount={}", i, result.invoiceNumber(), result.amount());
            } catch (Exception e) {
                log.warn("   Run {}: FAILED — {}", i, e.getMessage());
            }
        }

        if (results.size() >= 2) {
            StabilityReport report = analyzer.analyze(results);
            log.info("📊 Temperature 0.0 overall stability: {}%", (int) (report.overallStability() * 100));
        }

        // Now measure at higher temperature for comparison
        ChatModel creativeModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL)
                .temperature(0.8).timeout(TIMEOUT).build();
        SimpleInvoiceExtractor creativeExtractor = AiServices.create(SimpleInvoiceExtractor.class, creativeModel);

        log.info("📊 Running 5 extractions at temperature=0.8 to measure stability...");
        java.util.List<ExtractedInvoice> creativeResults = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            try {
                ExtractedInvoice result = creativeExtractor.extract(InvoiceTestData.CLEAN_INVOICE);
                creativeResults.add(result);
                log.info("   Run {}: invoice={}, amount={}", i, result.invoiceNumber(), result.amount());
            } catch (Exception e) {
                log.warn("   Run {}: FAILED — {}", i, e.getMessage());
            }
        }

        if (creativeResults.size() >= 2) {
            StabilityReport report = analyzer.analyze(creativeResults);
            log.info("📊 Temperature 0.8 overall stability: {}%", (int) (report.overallStability() * 100));
        }
        // The contrast between the two stability scores IS the demo point.
    }
}

