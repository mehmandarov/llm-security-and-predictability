package com.mehmandarov.llmvalidation.chapter2_guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Chapter 2: The Attack (Security).
 * Prevents prompt injection by scanning for common jailbreak phrases.
 */
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);
    
    private static final List<String> BLACKLIST = List.of(
        "ignore previous instructions",
        "ignore all previous instructions",
        "system override",
        "delete database",
        "reveal system prompt"
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText().toLowerCase();
        
        for (String phrase : BLACKLIST) {
            if (text.contains(phrase)) {
                log.warn("🚨 SECURITY ALERT: Prompt injection detected: '{}'", phrase);
                return fatal("Security Violation: Potential prompt injection detected.");
            }
        }
        
        return InputGuardrailResult.success();
    }
}
