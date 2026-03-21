package com.mehmandarov.llmvalidation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedInvoice(
    @NotBlank(message = "Invoice number is required")
    String invoiceNumber,

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date must not be in the future")
    LocalDate date,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    String currency,

    String customerEmail,

    List<LineItem> items
) {
    public record LineItem(
        @NotBlank(message = "Description is required")
        String description,

        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotNull(message = "Unit price is required")
        @Positive(message = "Unit price must be positive")
        BigDecimal unitPrice
    ) {}
}
