package com.mehmandarov.llmvalidation.chapter2_guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Chapter 2: The Attack (Security).
 * Prevents prompt injection by scanning for common jailbreak phrases
 * and detects sandwich-delimiter breakout attempts.
 *
 * <h3>Two layers of detection</h3>
 * <ol>
 *   <li><b>Phrase blacklist</b> — classic jailbreak strings like "ignore previous instructions".</li>
 *   <li><b>Delimiter breakout</b> — the closing {@code </user_input>} tag should <em>never</em>
 *       appear in raw user input. When running inside the LangChain4j pipeline the guardrail
 *       inspects the original template variables (before rendering) so the template's own tag
 *       does not trigger a false positive.</li>
 * </ol>
 */
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    /** Jailbreak phrases — checked against the (rendered) user message text. */
    private static final List<String> BLACKLIST = List.of(
        "ignore previous instructions",
        "ignore all previous instructions",
        "system override",
        "delete database",
        "reveal system prompt"
    );

    /** Delimiter tags that must never appear in *raw* user input. */
    private static final List<String> DELIMITER_BLACKLIST = List.of(
        "</user_input>"
    );

    /**
     * Pipeline entry-point — called by LangChain4j when this guardrail is
     * wired via {@code @InputGuardrails}. We can access the raw template
     * variables here, which lets us check for delimiter breakout without
     * tripping on the template's own tags.
     */
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        // 1. Standard blacklist check on the rendered message
        InputGuardrailResult blacklistResult = checkBlacklist(request.userMessage().singleText());
        if (!blacklistResult.isSuccess()) {
            return blacklistResult;
        }

        // 2. Delimiter breakout — inspect the RAW user-supplied variables,
        //    not the rendered template (which naturally contains </user_input>).
        Map<String, Object> variables = request.requestParams().variables();
        if (variables != null) {
            for (Object value : variables.values()) {
                if (value instanceof String rawInput) {
                    InputGuardrailResult delimResult = checkDelimiters(rawInput);
                    if (!delimResult.isSuccess()) {
                        return delimResult;
                    }
                }
            }
        }

        return success();
    }

    /**
     * Standalone entry-point — called directly (e.g. in unit tests) without
     * a template. Here the text IS the raw user input, so both blacklist
     * and delimiter checks apply directly.
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();

        InputGuardrailResult blacklistResult = checkBlacklist(text);
        if (!blacklistResult.isSuccess()) {
            return blacklistResult;
        }

        return checkDelimiters(text);
    }

    // ── internal helpers ──────────────────────────────────────────────

    private InputGuardrailResult checkBlacklist(String text) {
        String lower = text.toLowerCase();
        for (String phrase : BLACKLIST) {
            if (lower.contains(phrase)) {
                log.warn("🚨 SECURITY ALERT: Prompt injection detected: '{}'", phrase);
                return fatal("Security Violation: Potential prompt injection detected.");
            }
        }
        return success();
    }

    private InputGuardrailResult checkDelimiters(String text) {
        String lower = text.toLowerCase();
        for (String tag : DELIMITER_BLACKLIST) {
            if (lower.contains(tag)) {
                log.warn("🚨 SECURITY ALERT: Sandwich breakout detected! '{}' found in user input.", tag);
                return fatal("Security Violation: Sandwich breakout detected.");
            }
        }
        return success();
    }
}
