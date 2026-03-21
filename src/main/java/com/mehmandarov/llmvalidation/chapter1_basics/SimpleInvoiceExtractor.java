package com.mehmandarov.llmvalidation.chapter1_basics;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Chapter 1: The Basics.
 * Simple, structured extraction. No guardrails, no validation.
 *
 * <p>This is a LangChain4j <b>AI Service</b> interface — no manual implementation needed.
 * At runtime, {@code AiServices.create(SimpleInvoiceExtractor.class, chatModel)} generates
 * a proxy that sends the templated prompt to the LLM and deserializes the response into
 * {@link ExtractedInvoice}.</p>
 */
public interface SimpleInvoiceExtractor {

    @SystemMessage("You are a helpful assistant that extracts invoice data.")
    @UserMessage("Extract data from this text: {{text}}")
    ExtractedInvoice extract(@V("text") String text);
}
