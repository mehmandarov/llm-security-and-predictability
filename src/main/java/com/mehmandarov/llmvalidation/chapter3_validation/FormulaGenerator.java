package com.mehmandarov.llmvalidation.chapter3_validation;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Chapter 3: Code Execution — asks the LLM to generate an arithmetic formula
 * instead of computing the answer itself.
 *
 * <p>We then evaluate the formula deterministically with {@link ExpressionEvaluator}.
 * The generation is probabilistic, but the execution is not.</p>
 */
public interface FormulaGenerator {

    @SystemMessage("""
        You are a helpful assistant. When asked to calculate a total from line items,
        respond ONLY with the arithmetic expression — nothing else.
        Example: if items are $100, $200, and $50, respond with: 100 + 200 + 50
        Do NOT include dollar signs, currency, equals signs, or any other text.
        ONLY the arithmetic expression with + signs between the numbers.
        """)
    @UserMessage("Generate the arithmetic expression to calculate the total for these line items: {{it}}")
    String generateFormula(String lineItems);
}

