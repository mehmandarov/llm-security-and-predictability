package com.mehmandarov.llmvalidation.chapter6_bonus_mirror;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The "Mirror" component.
 * Takes structured data and reconstructs it back into natural language.
 */
public interface Reconstructor {

    @SystemMessage("""
        You are a quality assurance assistant. 
        Based ONLY on the provided JSON data, write a very brief summary of the invoice.
        Include the invoice number, total amount, and a list of items.
        Do NOT add any information that is not in the JSON.
        """)
    @UserMessage("JSON Data: {{json}}")
    String reconstruct(@V("json") String json);
}

