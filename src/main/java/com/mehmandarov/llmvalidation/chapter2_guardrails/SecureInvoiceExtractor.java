package com.mehmandarov.llmvalidation.chapter2_guardrails;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Chapter 2: The Attack (Security).
 * Secure extractor with input and output guardrails.
 *
 * <p>Same AI Service proxy pattern as {@link com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor},
 * but with {@code @InputGuardrails} and {@code @OutputGuardrails} that run <b>before</b>
 * the LLM sees the message and <b>after</b> it responds, respectively.</p>
 */
@InputGuardrails(PromptInjectionGuardrail.class)
@OutputGuardrails(PiiGuardrail.class)
public interface SecureInvoiceExtractor {

    @SystemMessage("You are a helpful assistant that extracts invoice data.")
    @UserMessage("Extract data from this text: {{text}}")
    ExtractedInvoice extract(@V("text") String text);
}
