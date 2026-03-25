package com.mehmandarov.llmvalidation.chapter6_bonus_mirror;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The "Verification" component.
 * Compares original source text with the reconstructed summary.
 */
public interface FaithfulnessChecker {

    @SystemMessage("""
        You are a data integrity auditor.
        Compare the ORIGINAL_TEXT with the RECONSTRUCTED_SUMMARY.
        Identify if any critical information (invoice #, amount, or line items) present 
        in the ORIGINAL_TEXT is missing or incorrect in the RECONSTRUCTED_SUMMARY.
        
        Return a score between 0.0 (completely unfaithful) and 1.0 (perfectly faithful).
        Return ONLY the numeric score.
        """)
    @UserMessage("""
        ORIGINAL_TEXT:
        {{original}}
        
        RECONSTRUCTED_SUMMARY:
        {{reconstructed}}
        """)
    double checkFaithfulness(@V("original") String original, @V("reconstructed") String reconstructed);
}

