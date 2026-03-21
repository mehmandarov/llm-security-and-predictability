package com.mehmandarov.llmvalidation.chapter2_guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 2: The Attack (Security).
 * Prevents PII leakage by redacting sensitive information from the output.
 */
public class PiiGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PiiGuardrail.class);
    
    // Regex for basic PII patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"
    );
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\d{3}-\\d{2}-\\d{4}"
    );
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"
    );

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text();
        boolean modified = false;

        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        if (emailMatcher.find()) {
            log.info("🛡️ PII DETECTED: Redacting email address.");
            text = emailMatcher.replaceAll("[REDACTED_EMAIL]");
            modified = true;
        }

        Matcher ssnMatcher = SSN_PATTERN.matcher(text);
        if (ssnMatcher.find()) {
            log.info("🛡️ PII DETECTED: Redacting SSN.");
            text = ssnMatcher.replaceAll("[REDACTED_SSN]");
            modified = true;
        }

        Matcher ccMatcher = CREDIT_CARD_PATTERN.matcher(text);
        if (ccMatcher.find()) {
            log.info("🛡️ PII DETECTED: Redacting credit card number.");
            text = ccMatcher.replaceAll("[REDACTED_CC]");
            modified = true;
        }
        
        if (modified) {
            return OutputGuardrailResult.successWith(text);
        }
        
        return OutputGuardrailResult.success();
    }
}
