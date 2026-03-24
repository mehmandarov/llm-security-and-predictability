package com.mehmandarov.llmvalidation.chapter3_validation;

import com.mehmandarov.llmvalidation.model.ExtractedInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Chapter 3: Deterministic Post-Processing.
 *
 * <p>Normalizes LLM output so "close enough" becomes identical. The model says
 * {@code $1,500} one time and {@code 1500.00} the next — normalization makes
 * both {@code 1500.00}. Pure Java, no LLM involved.</p>
 *
 * <h3>What it normalizes</h3>
 * <ul>
 *   <li><b>Amounts</b> → 2 decimal places, trailing zeros stripped via {@code setScale(2)}</li>
 *   <li><b>Strings</b> → trimmed, normalized whitespace</li>
 *   <li><b>Currency</b> → uppercase</li>
 *   <li><b>Line items</b> → same amount normalization applied to unit prices</li>
 * </ul>
 */
public class OutputNormalizer {

    private static final Logger log = LoggerFactory.getLogger(OutputNormalizer.class);

    /**
     * Returns a new {@link ExtractedInvoice} with all fields normalized.
     * The original is not modified (records are immutable).
     */
    public ExtractedInvoice normalize(ExtractedInvoice invoice) {
        if (invoice == null) {
            return null;
        }

        String invoiceNumber = normalizeString(invoice.invoiceNumber());
        BigDecimal amount = normalizeAmount(invoice.amount());
        String currency = normalizeCurrency(invoice.currency());
        String email = normalizeString(invoice.customerEmail());
        List<ExtractedInvoice.LineItem> items = normalizeItems(invoice.items());

        ExtractedInvoice normalized = new ExtractedInvoice(
                invoiceNumber,
                invoice.date(),  // dates don't need normalization — they're already LocalDate
                amount,
                currency,
                email,
                items
        );

        log.debug("📐 Normalized invoice: {} → {}", invoice, normalized);
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        // Trim, collapse multiple whitespace into single space
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            return null;
        }
        return currency.trim().toUpperCase();
    }

    private List<ExtractedInvoice.LineItem> normalizeItems(List<ExtractedInvoice.LineItem> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(item -> new ExtractedInvoice.LineItem(
                        normalizeString(item.description()),
                        item.quantity(),
                        normalizeAmount(item.unitPrice())
                ))
                .toList();
    }
}

