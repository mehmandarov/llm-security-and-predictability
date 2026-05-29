package com.mehmandarov.llmvalidation.chapter1_basics;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Chapter 1: Structured-Output Prompting — Improving Output Quality.
 *
 * <p>Where {@link SimpleInvoiceExtractor} just asks "extract invoice data", this
 * extractor hands the model the four levers that narrow the distribution of
 * possible responses:</p>
 * <ol>
 *   <li><b>An explicit schema</b> with exact key names — prevents field-name drift
 *       ({@code invoice_no} vs {@code invoiceNumber}) so the JSON maps cleanly onto
 *       {@link ExtractedInvoice}.</li>
 *   <li><b>Format directives</b> (ISO-8601 dates, ISO-4217 currency, no symbols) —
 *       prevents parse failures on {@code LocalDate} / {@code BigDecimal}.</li>
 *   <li><b>Behavioural rules</b> ("copy the total verbatim, never recalculate") —
 *       suppresses the hallucinated-math problem.</li>
 *   <li><b>A one-shot worked example</b> — removes ambiguity about shape, nesting,
 *       and null handling.</li>
 * </ol>
 *
 * <p>This <em>narrows</em> the output distribution; it never collapses it to a point.
 * The model can still return a future date or a wrong total <em>in perfectly valid
 * JSON</em>. That is the bridge to the next layers: prompt for structure here, then
 * verify with code (e.g. {@code StrictValidator}). Prompt for structure, then trust
 * code — not the model.</p>
 *
 * <p>Like {@link SimpleInvoiceExtractor}, this is a LangChain4j <b>AI Service</b>
 * interface — no manual implementation. {@code AiServices.create(
 * StructuredInvoiceExtractor.class, chatModel)} generates a proxy that sends the
 * templated prompt and deserializes the response into {@link ExtractedInvoice}.</p>
 */
public interface StructuredInvoiceExtractor {

    @SystemMessage("""
        You are an invoice extraction service. Extract data from the user's text
        into JSON that EXACTLY matches this schema. Do not add, rename, or omit fields.

        Schema (all keys required unless marked optional):
        {
          "invoiceNumber": string,        // e.g. "INV-2024-001"
          "date":          string,        // ISO-8601 date, "YYYY-MM-DD"
          "amount":        number,        // total due, decimal, no currency symbol
          "currency":      string,        // ISO-4217 code, e.g. "USD"
          "customerEmail": string|null,   // optional; null if not present
          "items": [
            {
              "description": string,
              "quantity":    integer,     // > 0
              "unitPrice":   number       // > 0, decimal
            }
          ]
        }

        Rules:
        - Output ONLY the JSON object. No prose, no markdown fences.
        - Use the date exactly as written in the document; do NOT invent or shift it.
        - Copy the stated total into "amount" verbatim; do NOT recalculate it.
        - If a value is missing, use null for customerEmail — never guess.

        Example:
        Input:  "Globex #INV-9 dated 2024-01-02. Widget x2 @ $10. Total $20 EUR."
        Output: {"invoiceNumber":"INV-9","date":"2024-01-02","amount":20.00,\
                 "currency":"EUR","customerEmail":null,\
                 "items":[{"description":"Widget","quantity":2,"unitPrice":10.00}]}
        """)
    @UserMessage("""
        Extract invoice data from the following text:

        <user_input>
        {{text}}
        </user_input>
        """)
    ExtractedInvoice extract(@V("text") String text);
}

