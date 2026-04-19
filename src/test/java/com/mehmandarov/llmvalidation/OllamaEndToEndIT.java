package com.mehmandarov.llmvalidation;

import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.CanaryInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.CanaryTokenGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.FortifiedInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter2_guardrails.InputLengthGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.IntentClassifier;
import com.mehmandarov.llmvalidation.chapter2_guardrails.OutputFormatGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PiiGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.PromptInjectionGuardrail;
import com.mehmandarov.llmvalidation.chapter2_guardrails.SandwichedInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter3_validation.StrictValidator;
import com.mehmandarov.llmvalidation.chapter3_validation.InvoiceCalculatorTool;
import com.mehmandarov.llmvalidation.chapter3_validation.ExpressionEvaluator;
import com.mehmandarov.llmvalidation.chapter3_validation.FormulaGenerator;
import com.mehmandarov.llmvalidation.chapter3_validation.ToolAwareInvoiceExtractor;
import com.mehmandarov.llmvalidation.chapter4_correction.CorrectiveExtractor;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus;
import com.mehmandarov.llmvalidation.chapter5_consensus.MultiModelConsensus.ConsensusResult;
import com.mehmandarov.llmvalidation.chapter5_consensus.SafeExtractionPipeline;
import com.mehmandarov.llmvalidation.chapter5_consensus.SafeExtractionPipeline.ExtractionOutcome;
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
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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
 *   <li>{@code ollama pull gemma4:e2b} (small &amp; fast for demos)</li>
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
    private static final String MODEL = "gemma4:e2b";
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
        // First: send the injection to an UNGUARDED extractor to show the real danger
        SimpleInvoiceExtractor unguarded = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        log.info("🚨 Sending injection attack to an UNGUARDED real model...");
        try {
            ExtractedInvoice raw = unguarded.extract(InvoiceTestData.INJECTION_ATTACK);
            log.warn("⚠️ Unguarded model processed the injection and returned: {}", raw);
            log.warn("   This is why we need guardrails — the model can't protect itself.");
        } catch (Exception e) {
            log.info("   Model choked on the injection: {}", e.getMessage());
        }

        // Now: the guardrail blocks the same attack BEFORE the model ever sees it
        PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail();
        InputGuardrailResult result = guardrail.validate(UserMessage.from(InvoiceTestData.INJECTION_ATTACK));
        log.info("🛡️ Guardrail verdict: blocked={}", !result.isSuccess());
        assertThat(result.isSuccess()).isFalse();
        log.info("✅ With the guardrail, the injection never reaches the model.");
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
    @DisplayName("Ch2 · Prompt stuffing is blocked — oversized input never reaches the model")
    void chapter2_promptStuffingBlocked() {
        // The guardrail blocks this BEFORE the model — saving time and tokens
        InputLengthGuardrail guardrail = new InputLengthGuardrail();
        InputGuardrailResult result = guardrail.validate(UserMessage.from(InvoiceTestData.PROMPT_STUFFING));

        log.info("🚨 Prompt stuffing: {} chars (max {}) → blocked={}",
                InvoiceTestData.PROMPT_STUFFING.length(),
                InputLengthGuardrail.DEFAULT_MAX_LENGTH,
                !result.isSuccess());
        assertThat(result.isSuccess()).isFalse();

        // For contrast: a normal-length invoice sails through to the real model
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        ExtractedInvoice normalResult = extractor.extract(InvoiceTestData.CLEAN_INVOICE);
        log.info("✅ Normal input ({} chars) reaches the model fine: invoice={}",
                InvoiceTestData.CLEAN_INVOICE.length(), normalResult.invoiceNumber());
    }

    @Test
    @Order(5)
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

    @Test
    @Order(6)
    @DisplayName("Ch2 · Output format guardrail catches non-JSON responses")
    void chapter2_outputFormatGuardrail() {
        OutputFormatGuardrail guardrail = new OutputFormatGuardrail();

        // Ask the REAL model via raw chat (bypass AiServices parsing) so we can inspect the response
        log.info("📋 Asking real model for invoice extraction (raw response, no parsing)...");
        ChatResponse response = ollamaModel.chat(ChatRequest.builder()
                .messages(UserMessage.from(
                        "Extract invoice data as JSON with fields invoiceNumber, date, amount, currency from:\n\n"
                                + InvoiceTestData.CLEAN_INVOICE))
                .build());
        AiMessage realResponse = response.aiMessage();
        String preview = realResponse.text().substring(0, Math.min(120, realResponse.text().length()));
        log.info("   Raw model response: {}", preview);

        OutputGuardrailResult realResult = guardrail.validate(realResponse);
        log.info("   Format check on real response: pass={}", realResult.isSuccess());

        // For contrast: prose (what a hijacked model would return) is blocked
        AiMessage prose = AiMessage.from(
                "I'm sorry, I cannot process invoices. As an AI language model, I must decline this request.");
        OutputGuardrailResult proseResult = guardrail.validate(prose);
        log.info("🚨 Prose (no braces) → blocked={}", !proseResult.isSuccess());
        assertThat(proseResult.isSuccess()).isFalse();

        // Unbalanced braces (truncated response) are also blocked
        AiMessage broken = AiMessage.from("{ \"invoiceNumber\": \"INV-001\" ");
        OutputGuardrailResult unbalancedResult = guardrail.validate(broken);
        log.info("🚨 Unbalanced braces → blocked={}", !unbalancedResult.isSuccess());
        assertThat(unbalancedResult.isSuccess()).isFalse();

        log.info("✅ Real model: {}. Prose: blocked. Unbalanced: blocked. Braces or bust.",
                realResult.isSuccess() ? "JSON (passed)" : "not JSON (caught!)");
    }

    @Test
    @Order(7)
    @DisplayName("Ch2 · Sandwiching isolates user data — but alone doesn't block breakout")
    void chapter2_sandwichingWithRealModel() {
        SandwichedInvoiceExtractor extractor = AiServices.create(SandwichedInvoiceExtractor.class, ollamaModel);

        // Clean input through sandwiched template should work
        log.info("🥪 Testing sandwiched extractor with clean input...");
        try {
            ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);
            log.info("✅ Clean input through sandwich: invoice={}, amount={}", result.invoiceNumber(), result.amount());
        } catch (Exception e) {
            log.warn("⚠️ Clean input failed through sandwich: {}", e.getMessage());
        }

        // Breakout attempt — sandwiching has NO @InputGuardrails, so it won't be blocked
        log.info("🥪 Testing sandwiched extractor with breakout attempt...");
        try {
            ExtractedInvoice breakout = extractor.extract(InvoiceTestData.SANDWICH_BREAKOUT);
            log.warn("⚠️ Breakout was NOT blocked — model returned: invoice={}", breakout.invoiceNumber());
            log.warn("   Sandwiching is a HINT to the model, not enforcement.");
            log.warn("   Layer 6 (Fortified) adds the guardrails that catch this.");
        } catch (Exception e) {
            log.info("💥 Model choked on breakout input: {}", e.getMessage());
            log.info("   The model rejected it on its own — but that's not guaranteed. We need guardrails.");
        }
    }

    @Test
    @Order(8)
    @DisplayName("Ch2 · Bouncer classifies intent with a real model")
    void chapter2_bouncerWithRealModel() {
        IntentClassifier bouncer = AiServices.create(IntentClassifier.class, ollamaModel);

        log.info("🚪 Testing the Bouncer with clean input...");
        try {
            IntentClassifier.Intent cleanIntent = bouncer.classify(InvoiceTestData.CLEAN_INVOICE);
            log.info("   Clean input → {}", cleanIntent);

            IntentClassifier.Intent maliciousIntent = bouncer.classify(InvoiceTestData.INJECTION_ATTACK);
            log.info("   Injection attack → {}", maliciousIntent);

            if (maliciousIntent == IntentClassifier.Intent.MALICIOUS_INJECTION) {
                log.info("✅ Bouncer correctly identified the attack!");
            } else {
                log.warn("⚠️ Bouncer classified attack as {} — small models may miss subtle injections.", maliciousIntent);
            }
        } catch (Exception e) {
            log.warn("⚠️ Bouncer classification failed: {}", e.getMessage());
            log.warn("   Some small models struggle with enum classification. That's why we layer defenses.");
        }
    }

    @Test
    @Order(9)
    @DisplayName("Ch2 · Fortified extractor — full defense stack with a real model")
    void chapter2_fortifiedWithRealModel() {
        FortifiedInvoiceExtractor extractor = AiServices.create(FortifiedInvoiceExtractor.class, ollamaModel);

        // Clean input should pass all guards
        log.info("🏰 Testing Fortified extractor with clean input...");
        try {
            ExtractedInvoice result = extractor.extract(InvoiceTestData.CLEAN_INVOICE);
            log.info("✅ Clean input passed all guards: invoice={}, amount={}", result.invoiceNumber(), result.amount());
        } catch (Exception e) {
            log.warn("⚠️ Clean input was blocked: {} — this can happen if the model's output triggers a guardrail.", e.getMessage());
        }

        // Injection should be blocked BEFORE hitting the model
        log.info("🏰 Testing Fortified extractor with injection attack...");
        try {
            extractor.extract(InvoiceTestData.INJECTION_ATTACK);
            log.warn("⚠️ Injection was NOT blocked — the guardrails failed to catch it.");
        } catch (Exception e) {
            log.info("✅ Injection blocked by the full defense stack: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 3 — The Hallucination: validation catches what the LLM gets wrong
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
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
    @Order(11)
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

    @Test
    @Order(12)
    @DisplayName("Ch3 · Tool calling — LLM uses a calculator instead of guessing math")
    void chapter3_toolCallingWithRealModel() {
        // Wire the tool into the extractor — the LLM can call calculateTotal() instead of guessing
        InvoiceCalculatorTool calculator = new InvoiceCalculatorTool();
        ToolAwareInvoiceExtractor extractor = AiServices.builder(ToolAwareInvoiceExtractor.class)
                .chatModel(ollamaModel)
                .tools(calculator)
                .build();

        String invoiceWithLineItems = """
            INVOICE #INV-TOOL-001
            Date: 2024-06-15
            
            Items:
            - Consulting Services: $750.00
            - Software License: $250.00
            - Support Package: $125.50
            
            Currency: USD
            """;

        log.info("🧮 Asking model to extract invoice WITH tool access...");
        try {
            ExtractedInvoice result = extractor.extract(invoiceWithLineItems);
            log.info("📋 Extracted: invoice={}, amount={}, currency={}",
                    result.invoiceNumber(), result.amount(), result.currency());

            // If the model used the tool, the total will be exactly 1125.50
            // If it guessed, it might be different — that's the demo point!
            if (result.amount() != null && result.amount().compareTo(new java.math.BigDecimal("1125.50")) == 0) {
                log.info("✅ Model used the tool — total is exactly $1125.50 (deterministic)");
            } else {
                log.warn("⚠️ Model may not have used the tool — amount is {} (expected 1125.50)", result.amount());
                log.warn("   This is why we validate: even with tools, verify the result.");
            }
        } catch (Exception e) {
            log.warn("⚠️ Tool-calling extraction failed: {}", e.getMessage());
            log.warn("   Some small models don't support function calling well. That's the reality.");
        }
    }

    @Test
    @Order(13)
    @DisplayName("Ch3 · Code execution — LLM generates formula, Java evaluates it")
    void chapter3_codeExecutionWithRealModel() {
        FormulaGenerator generator = AiServices.create(FormulaGenerator.class, ollamaModel);
        ExpressionEvaluator evaluator = new ExpressionEvaluator();

        String lineItems = "Consulting $750, License $250, Support $125.50";
        BigDecimal expectedTotal = new BigDecimal("1125.50");

        log.info("🧮 Asking model to GENERATE formula (not compute the answer)...");
        try {
            String formula = generator.generateFormula(lineItems);
            log.info("📝 LLM generated formula: '{}'", formula);

            BigDecimal result = evaluator.evaluate(formula);
            log.info("🧮 Java evaluated: {} = {}", formula, result);

            if (evaluator.verify(formula, expectedTotal)) {
                log.info("✅ Formula is correct — LLM wrote it, Java executed it deterministically.");
            } else {
                log.warn("⚠️ Formula evaluated to {} but expected {}. The generation was wrong, but we caught it!", result, expectedTotal);
                log.warn("   This is the point: a wrong formula is easier to debug than a wrong number.");
            }
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ LLM generated an unsafe or unparseable expression: {}", e.getMessage());
            log.warn("   The safety net caught it — no code injection possible.");
        } catch (Exception e) {
            log.warn("⚠️ Code execution test failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 4 — The Bargaining: self-correction loop with a real model
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(14)
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

    @Test
    @Order(15)
    @DisplayName("Ch4 · Self-correction with ambiguous date — real model resolves 01/02/2024")
    void chapter4_ambiguousDateWithRealModel() {
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        StrictValidator validator = new StrictValidator();
        CorrectiveExtractor corrective = new CorrectiveExtractor(extractor, validator, 3);

        log.info("📅 Asking model to extract an invoice with ambiguous date '01/02/2024'...");
        ExtractedInvoice result = corrective.extract(InvoiceTestData.AMBIGUOUS_DATE);
        log.info("📋 Extracted: date={}, invoice={}", result.date(), result.invoiceNumber());

        ValidationResult validation = validator.validate(result);
        if (validation.isValid()) {
            log.info("✅ Model resolved the ambiguous date to: {} — valid!", result.date());
        } else {
            log.warn("⚠️ Ambiguous date caused validation errors:");
            validation.errors().forEach(e -> log.warn("   ❌ [{}] {}", e.category(), e.message()));
        }
        // Either way, the validator gives us a deterministic answer about whether the result is usable.
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 5 — The Council: consensus with the same model (3 calls)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("Ch5 · Consensus across same model at different temperatures")
    void chapter5_consensusSameModelDifferentTemperatures() {
        // Use the same model at different temperatures to simulate diversity
        ChatModel deterministic = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(0.0).timeout(TIMEOUT).build();
        ChatModel creative = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(0.7).timeout(TIMEOUT).build();
        ChatModel wild = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(1.0).timeout(TIMEOUT).build();

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(deterministic, creative, wild));

        ConsensusResult result = consensus.runConsensus(InvoiceTestData.CLEAN_INVOICE);

        log.info("🗳️ Same-model consensus: highConfidence={}, confidence={}%",
                result.isHighConfidence(), (int) (result.confidence() * 100));
        if (result.consensus() != null) {
            log.info("   Invoice: {}", result.consensus().invoiceNumber());
            log.info("   Amount:  {}", result.consensus().amount());
        }
        // With clean input, even diverse temperatures should mostly agree
    }

    @Test
    @Order(17)
    @DisplayName("Ch5 · Consensus across DIFFERENT models (true multi-model)")
    void chapter5_consensusDifferentModels() {
        // True multi-model consensus: different architectures, same question
        String modelA = "gemma4:e2b";
        String modelB = "llama3.2:1b";

        ChatModel gemma = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(modelA).temperature(0.0).timeout(TIMEOUT).build();
        ChatModel llama;
        try {
            llama = OllamaChatModel.builder()
                    .baseUrl(OLLAMA_URL).modelName(modelB).temperature(0.0).timeout(TIMEOUT).build();
        } catch (Exception e) {
            log.warn("⚠️ Model '{}' not available — run `ollama pull {}` to enable this test. Skipping.", modelB, modelB);
            return;
        }

        log.info("🗳️ True multi-model consensus: {} + {} + {} (at different temp)", modelA, modelB, modelA);

        // Three voters: gemma, llama, and gemma at higher temp for a tiebreaker
        ChatModel gemmaTiebreaker = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(modelA).temperature(0.5).timeout(TIMEOUT).build();

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(gemma, llama, gemmaTiebreaker));

        try {
            ConsensusResult result = consensus.runConsensus(InvoiceTestData.CLEAN_INVOICE);

            log.info("🗳️ Multi-model consensus: highConfidence={}, confidence={}%",
                    result.isHighConfidence(), (int) (result.confidence() * 100));
            if (result.consensus() != null) {
                log.info("   Invoice: {}", result.consensus().invoiceNumber());
                log.info("   Amount:  {}", result.consensus().amount());
                log.info("   Date:    {}", result.consensus().date());
            }
            log.info("   🎯 Different architectures agreeing is stronger evidence than the same model agreeing with itself.");
        } catch (Exception e) {
            log.warn("⚠️ Multi-model consensus failed (model may not be pulled): {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 5 (cont.) — Consensus on noisy data: does agreement hold?
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(18)
    @DisplayName("Ch5 · Consensus with messy OCR — does agreement hold on noisy data?")
    void chapter5_consensusWithMessyOcr() {
        ChatModel deterministic = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(0.0).timeout(TIMEOUT).build();
        ChatModel creative = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(0.7).timeout(TIMEOUT).build();
        ChatModel wild = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL).modelName(MODEL).temperature(1.0).timeout(TIMEOUT).build();

        MultiModelConsensus consensus = new MultiModelConsensus(List.of(deterministic, creative, wild));

        log.info("🗳️ Consensus on MESSY OCR input (intentionally noisy data)...");
        try {
            ConsensusResult result = consensus.runConsensus(InvoiceTestData.MESSY_OCR);
            log.info("🗳️ Messy OCR consensus: highConfidence={}, confidence={}%",
                    result.isHighConfidence(), (int) (result.confidence() * 100));
            if (result.consensus() != null) {
                log.info("   Invoice: {}", result.consensus().invoiceNumber());
                log.info("   Amount:  {}", result.consensus().amount());
            }
            if (result.isHighConfidence()) {
                log.info("   ✅ Models agreed despite noisy input — consensus provides robustness.");
            } else {
                log.warn("   ⚠️ Low confidence — models disagreed on messy OCR. This is the reality of noisy data.");
            }
        } catch (Exception e) {
            log.warn("⚠️ Consensus on messy OCR failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 5 (cont.) — Measuring the Chaos: stability analysis
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(19)
    @DisplayName("🎲 Ch5 · Stability analysis — measuring variance across repeated runs")
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

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 5 (cont.) — The Safe Extraction Pipeline (Capstone)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Ch5 · SafeExtractionPipeline — full stack: extract → validate → correct → verdict")
    void chapter5_safeExtractionPipelineWithRealModel() {
        SimpleInvoiceExtractor extractor = AiServices.create(SimpleInvoiceExtractor.class, ollamaModel);
        SafeExtractionPipeline pipeline = new SafeExtractionPipeline(extractor, new StrictValidator(), 3);

        // Clean invoice should be ACCEPTED
        log.info("🏗️ Running SafeExtractionPipeline on clean invoice...");
        ExtractionOutcome cleanOutcome = pipeline.process(InvoiceTestData.CLEAN_INVOICE);
        log.info("   Status: {}, Invoice: {}, Reason: {}",
                cleanOutcome.status(),
                cleanOutcome.invoice() != null ? cleanOutcome.invoice().invoiceNumber() : "N/A",
                cleanOutcome.reason());

        // Future-date invoice may be NEEDS_REVIEW or ACCEPTED (if self-correction fixes it)
        log.info("🏗️ Running SafeExtractionPipeline on future-date hallucination...");
        ExtractionOutcome futureOutcome = pipeline.process(InvoiceTestData.FUTURE_DATE_HALLUCINATION);
        log.info("   Status: {}, Invoice: {}, Reason: {}",
                futureOutcome.status(),
                futureOutcome.invoice() != null ? futureOutcome.invoice().invoiceNumber() : "N/A",
                futureOutcome.reason());

        // The point: the pipeline ALWAYS gives a clear status — no ambiguity for the caller.
        assertThat(cleanOutcome.status()).isNotNull();
        assertThat(futureOutcome.status()).isNotNull();
        log.info("✅ Pipeline always returns a typed verdict: {}, {}", cleanOutcome.status(), futureOutcome.status());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chapter 5 (cont.) — Deterministic Seeds
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(21)
    @DisplayName("🎲 Ch5 · Seed-based reproducibility — testing the model's determinism promise")
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
    //  Bonus — The Mirror Test: round-trip verification with a real model
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(22)
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
    @Order(23)
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

