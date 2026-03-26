package com.mehmandarov.llmvalidation.chapter3_validation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Chapter 3: Tool-Aware Invoice Extractor.
 *
 * <p>Same extraction task as {@code SimpleInvoiceExtractor}, but with access
 * to deterministic tools (e.g. {@link InvoiceCalculatorTool}). The LLM can
 * call {@code calculateTotal} or {@code calculateTax} instead of guessing
 * the math — the computation is pure Java, not probabilistic.</p>
 *
 * <p>Wire it up with:</p>
 * <pre>
 *   AiServices.builder(ToolAwareInvoiceExtractor.class)
 *       .chatModel(model)
 *       .tools(new InvoiceCalculatorTool())
 *       .build();
 * </pre>
 *
 * <p><b>Key insight for the talk:</b> Function calling turns the LLM into a
 * <em>decision maker</em> ("should I add these numbers?") while keeping the
 * <em>execution</em> deterministic. The LLM picks the tool; Java does the math.</p>
 */
public interface ToolAwareInvoiceExtractor {

    @SystemMessage("""
        You are a helpful assistant that extracts invoice data.
        
        IMPORTANT: When you need to calculate totals, taxes, or any arithmetic,
        you MUST use the provided tools instead of computing the values yourself.
        Never guess or estimate numbers — always call the appropriate tool.
        """)
    @UserMessage("Extract invoice data from the following text: {{it}}")
    ExtractedInvoice extract(String text);
}

