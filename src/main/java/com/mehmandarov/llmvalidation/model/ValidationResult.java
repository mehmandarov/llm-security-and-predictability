package com.mehmandarov.llmvalidation.model;

import java.util.Collections;
import java.util.List;

public record ValidationResult(boolean isValid, List<ValidationError> errors) {

    public static ValidationResult valid() {
        return new ValidationResult(true, Collections.emptyList());
    }

    public static ValidationResult invalid(List<ValidationError> errors) {
        return new ValidationResult(false, errors);
    }

    public record ValidationError(String category, String message) {}
}
