package com.mehmandarov.llmvalidation.chapter3_validation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Chapter 3: Deterministic Tools — Don't Let the LLM Do Math.
 *
 * <p>Instead of asking the LLM "what is 100 + 200 + 50?", give it a tool.
 * The LLM decides <em>when</em> to add, but the addition itself is
 * deterministic Java code. Zero hallucination risk for arithmetic.</p>
 *
 * <p>Wire this into an extractor via {@code AiServices.builder().tools(new InvoiceCalculatorTool())}.</p>
 */
public class InvoiceCalculatorTool {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCalculatorTool.class);

    @Tool("Calculate the total amount by summing a list of individual line item prices. " +
          "Use this whenever you need to compute a total from line items.")
    public BigDecimal calculateTotal(
            @P("list of individual line item prices") List<BigDecimal> prices) {
        BigDecimal total = prices.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        log.info("🧮 TOOL CALLED: calculateTotal({}) = {}", prices, total);
        return total;
    }

    @Tool("Calculate tax amount for a given subtotal and tax rate percentage. " +
          "Use this instead of computing tax yourself.")
    public BigDecimal calculateTax(
            @P("the subtotal amount") BigDecimal subtotal,
            @P("the tax rate as a percentage, e.g. 21.0 for 21%") BigDecimal taxRatePercent) {
        BigDecimal tax = subtotal
                .multiply(taxRatePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        log.info("🧮 TOOL CALLED: calculateTax({}, {}%) = {}", subtotal, taxRatePercent, tax);
        return tax;
    }
}

