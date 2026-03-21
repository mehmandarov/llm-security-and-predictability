package com.mehmandarov.llmvalidation.consensus;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-model consensus engine.
 * Queries multiple LLM models with the same prompt and uses majority voting
 * to determine the most likely correct answer.
 */
public class MultiModelConsensus {

    private static final Logger log = LoggerFactory.getLogger(MultiModelConsensus.class);
    private final List<ChatModel> models;

    public MultiModelConsensus(List<ChatModel> models) {
        this.models = models;
    }

    /**
     * Asks each model to extract invoice data, then votes on the result.
     * Fields are voted on independently (invoice number, amount, etc.).
     */
    public ConsensusResult extractWithConsensus(String text) {
        List<ExtractedInvoice> results = new ArrayList<>();

        for (int i = 0; i < models.size(); i++) {
            ChatModel model = models.get(i);
            try {
                log.info("   📤 Querying model {} of {}...", i + 1, models.size());
                ExtractedInvoice invoice = extractFromModel(model, text);
                results.add(invoice);
                log.info("   📥 Model {} responded: invoice={}, amount={}",
                        i + 1, invoice.invoiceNumber(), invoice.amount());
            } catch (Exception e) {
                log.warn("   ⚠️ Model {} failed: {}", i + 1, e.getMessage());
            }
        }

        if (results.isEmpty()) {
            return new ConsensusResult(false, null, 0.0);
        }

        // Majority vote on each field
        String invoiceNumber = majorityVote(results.stream()
                .map(ExtractedInvoice::invoiceNumber).collect(Collectors.toList()));
        BigDecimal amount = majorityVoteAmount(results.stream()
                .map(ExtractedInvoice::amount).collect(Collectors.toList()));
        String currency = majorityVote(results.stream()
                .map(ExtractedInvoice::currency).collect(Collectors.toList()));

        // Calculate confidence: fraction of models that agreed on the amount (the most critical field)
        long agreeing = results.stream()
                .filter(r -> r.amount() != null && r.amount().compareTo(amount) == 0)
                .count();
        double confidence = (double) agreeing / results.size();

        ExtractedInvoice consensus = new ExtractedInvoice(
                invoiceNumber,
                results.getFirst().date(), // use first non-null date
                amount,
                currency,
                null,
                Collections.emptyList()
        );

        return new ConsensusResult(confidence >= 0.6, consensus, confidence);
    }

    private ExtractedInvoice extractFromModel(ChatModel model, String text) {
        // Use AiServices to get structured output from each model
        SimpleExtractor extractor = AiServices.create(SimpleExtractor.class, model);
        return extractor.extract(text);
    }

    private <T> T majorityVote(List<T> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private BigDecimal majorityVoteAmount(List<BigDecimal> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(v -> v.stripTrailingZeros().toPlainString(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new BigDecimal(e.getKey()))
                .orElse(null);
    }

    /**
     * Internal interface for structured extraction from each model.
     */
    interface SimpleExtractor {
        @dev.langchain4j.service.SystemMessage("You are a helpful assistant that extracts invoice data.")
        @dev.langchain4j.service.UserMessage("Extract data from this text: {{text}}")
        ExtractedInvoice extract(@dev.langchain4j.service.V("text") String text);
    }

    public record ConsensusResult(boolean isHighConfidence, ExtractedInvoice consensus, double confidence) {}
}
