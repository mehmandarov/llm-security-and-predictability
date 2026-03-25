package com.mehmandarov.llmvalidation.chapter6_bonus_mirror;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bonus: The Mirror Test (Synthetic Verification).
 *
 * <p>Orchestrates the round-trip verification process:</p>
 * <ol>
 *   <li>Accepts extracted data (JSON).</li>
 *   <li>Reconstructs a natural language summary from that data (The Mirror).</li>
 *   <li>Compares the summary against the original source text.</li>
 *   <li>Assigns a "Faithfulness Score" to detect omissions or hallucinations.</li>
 * </ol>
 */
public class MirrorVerifier {

    private static final Logger log = LoggerFactory.getLogger(MirrorVerifier.class);
    private final Reconstructor reconstructor;
    private final FaithfulnessChecker checker;

    public MirrorVerifier(ChatModel model) {
        this.reconstructor = AiServices.create(Reconstructor.class, model);
        this.checker = AiServices.create(FaithfulnessChecker.class, model);
    }

    public VerificationResult verify(String originalText, String extractedJson) {
        log.info("🪞 Starting Mirror Test verification...");

        // Phase 1: Reconstruction
        log.info("   1. Reconstructing summary from JSON...");
        String summary = reconstructor.reconstruct(extractedJson);
        log.debug("   Summary: {}", summary);

        // Phase 2: Faithfulness Check
        log.info("   2. Comparing summary against original source...");
        double score = checker.checkFaithfulness(originalText, summary);
        
        log.info("   🏁 Faithfulness Score: {}%", (int) (score * 100));

        if (score < 0.8) {
            log.warn("⚠️ LOW FAITHFULNESS detected. The extraction may be incomplete or inaccurate.");
        } else {
            log.info("✅ FAITHFULNESS OK. Round-trip verified.");
        }

        return new VerificationResult(score, summary);
    }

    public record VerificationResult(double faithfulnessScore, String syntheticSummary) {}
}

