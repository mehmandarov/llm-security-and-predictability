package com.mehmandarov.llmvalidation.chapter2_guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chapter 2: Structural Integrity Check.
 *
 * <p>Verifies that the raw LLM response looks like valid JSON before LangChain4j
 * attempts to parse it into {@link com.mehmandarov.llmvalidation.model.ExtractedInvoice}.
 * A hijacked model typically outputs prose, apologies, or the canary token — none
 * of which is JSON. This guardrail catches that cheaply.</p>
 *
 * <p>The check is intentionally simple: the trimmed response must start with {@code {}
 * and end with {@code }}. This isn't a full JSON parser (LangChain4j does that later) —
 * it's a fast, zero-dependency gate that rejects obviously wrong output.</p>
 */
public class OutputFormatGuardrail implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(OutputFormatGuardrail.class);

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text().trim();

        if (!text.startsWith("{") || !text.endsWith("}")) {
            log.warn("🚨 OUTPUT FORMAT VIOLATION: Response is not JSON. First 100 chars: '{}'",
                    text.substring(0, Math.min(text.length(), 100)));
            return fatal("Format Violation: LLM response is not valid JSON. "
                    + "The model may have been hijacked or produced an unexpected format.");
        }

        // Quick bracket balance check
        long open = text.chars().filter(c -> c == '{').count();
        long close = text.chars().filter(c -> c == '}').count();
        if (open != close) {
            log.warn("🚨 OUTPUT FORMAT VIOLATION: Unbalanced JSON braces (open={}, close={})", open, close);
            return fatal("Format Violation: Unbalanced JSON braces in LLM response.");
        }

        log.debug("✅ Output format OK: starts/ends with braces, balanced.");
        return OutputGuardrailResult.success();
    }
}

