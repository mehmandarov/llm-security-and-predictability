package com.mehmandarov.llmvalidation.chapter5_consensus;

import com.mehmandarov.llmvalidation.consensus.MultiModelConsensus;
import com.mehmandarov.llmvalidation.consensus.MultiModelConsensus.ConsensusResult;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Chapter 5: The Council (Consensus).
 * Wraps the complex consensus logic for the demo.
 */
public class ConsensusManager {

    private static final Logger log = LoggerFactory.getLogger(ConsensusManager.class);
    private final MultiModelConsensus consensus;
    private final int modelCount;

    public ConsensusManager(List<ChatModel> models) {
        this.consensus = new MultiModelConsensus(models);
        this.modelCount = models.size();
    }

    public ConsensusResult runConsensus(String text) {
        log.info("🗳️ Starting consensus vote with {} models...", modelCount);
        ConsensusResult result = consensus.extractWithConsensus(text);
        
        if (result.isHighConfidence()) {
            log.info("✅ HIGH CONFIDENCE CONSENSUS REACHED!");
            log.info("   Invoice: {}", result.consensus().invoiceNumber());
            log.info("   Amount:  {}", result.consensus().amount());
            log.info("   Score:   {}%", (int)(result.confidence() * 100));
        } else {
            log.warn("⚠️ LOW CONFIDENCE. Manual review required.");
            log.warn("   Score:   {}%", (int)(result.confidence() * 100));
        }

        return result;
    }
}
