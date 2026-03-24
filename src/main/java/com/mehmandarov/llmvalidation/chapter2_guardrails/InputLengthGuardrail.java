package com.mehmandarov.llmvalidation.chapter2_guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 2: Prompt Stuffing Prevention.
 *
 * <p>Rejects inputs that exceed a configurable character limit. "Prompt stuffing"
 * pads the user input with junk to push the system prompt out of the model's
 * context window, effectively disabling all instructions. A simple length check
 * stops this before the LLM is ever called.</p>
 */
public class InputLengthGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(InputLengthGuardrail.class);

    /** Sensible default for a single-page invoice (~5,000 chars). */
    public static final int DEFAULT_MAX_LENGTH = 10_000;

    private final int maxLength;

    public InputLengthGuardrail() {
        this(DEFAULT_MAX_LENGTH);
    }

    public InputLengthGuardrail(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        int length = userMessage.singleText().length();

        if (length > maxLength) {
            log.warn("🚨 INPUT TOO LONG: {} chars (max {}). Possible prompt stuffing attack.",
                    length, maxLength);
            return fatal("Security Violation: Input length " + length
                    + " exceeds maximum allowed " + maxLength + " characters.");
        }

        log.debug("✅ Input length OK: {} chars (max {})", length, maxLength);
        return InputGuardrailResult.success();
    }
}

