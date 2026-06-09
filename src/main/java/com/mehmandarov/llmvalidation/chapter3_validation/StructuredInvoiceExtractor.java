package com.mehmandarov.llmvalidation.chapter3_validation;

import com.mehmandarov.llmvalidation.chapter1_basics.SimpleInvoiceExtractor;
import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Chapter 3: Guided Prompting — Add the Rules the Type System Can't Express.
 *
 * <p>This is the <b>first</b> predictability layer: before we validate with
 * {@link StrictValidator} or hand the math to a tool, we reduce how often the model
 * errs in the first place.</p>
 *
 * <p><b>What you do NOT need to do here:</b> restate the JSON schema. Because this
 * method returns {@link ExtractedInvoice}, LangChain4j's {@code AiServices} already
 * derives the schema from the record type and injects it into the prompt (and, on
 * providers that support it, enforces a native JSON-schema response format), then
 * deserializes the reply back into the record. {@link SimpleInvoiceExtractor} returns
 * the same type and gets that for free. Hand-writing the field names/types again would
 * be redundant, would <em>drift</em> from the record the moment a field changes, and
 * can even compete with the framework-injected schema on small local models.</p>
 *
 * <p><b>What you DO add here — the real delta over {@link SimpleInvoiceExtractor}:</b>
 * the <em>semantic</em> guidance the type system cannot encode:</p>
 * <ul>
 *   <li>"Use the date exactly as written; do NOT shift it" — a {@code LocalDate} field
 *       can't say this.</li>
 *   <li>"Copy the stated total verbatim; do NOT recalculate it" — the
 *       anti-hallucinated-math lever; {@code BigDecimal} can't forbid arithmetic.</li>
 *   <li>"If a value is missing, use null — never guess."</li>
 *   <li>"Output only JSON, no prose" — reinforces the framework's own instruction.</li>
 * </ul>
 *
 * <p>Even this only <em>narrows</em> the output distribution; it never collapses it to a
 * point. The model can still return a future date or a wrong total <em>in perfectly
 * valid JSON</em> — which is why this is prevention, not a guarantee. Detection comes
 * next: verify with code ({@link StrictValidator}), then keep the math deterministic
 * (tool calling / code execution). Guide the prompt, then trust code — not the model.</p>
 *
 * <p>The {@code <user_input>} delimiters carried over from the Chapter 2
 * {@code SandwichedInvoiceExtractor} keep this injection-resistant too — guiding the
 * output and isolating the input compose naturally.</p>
 */
public interface StructuredInvoiceExtractor {

    // NOTE: We deliberately do NOT restate the JSON schema here — the ExtractedInvoice
    // return type already drives schema generation + deserialization in AiServices.
    // What earns its place below is the semantic guidance the type system can't express.
    @SystemMessage("""
        You are an invoice extraction service. Extract the invoice data from the
        user's text. Follow these rules — they matter more than any format hint:

        - Output ONLY the JSON object. No prose, no markdown fences.
        - Use the date exactly as written in the document; do NOT invent or shift it.
        - Copy the stated total verbatim; do NOT recalculate or "fix" the math yourself.
        - If a value is missing, use null — never guess or fabricate it.
        """)
    @UserMessage("""
        Extract invoice data from the following text:

        <user_input>
        {{text}}
        </user_input>
        """)
    ExtractedInvoice extract(@V("text") String text);
}

