package com.mehmandarov.llmvalidation.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JUnit 5 condition that skips the test if Ollama is not reachable.
 * Usage: {@code @OllamaAvailable} on a test class or method.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OllamaAvailable.OllamaCondition.class)
public @interface OllamaAvailable {

    String url() default "http://localhost:11434";

    class OllamaCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String url = context.getTestClass()
                    .flatMap(cls -> java.util.Optional.ofNullable(cls.getAnnotation(OllamaAvailable.class)))
                    .map(OllamaAvailable::url)
                    .orElse("http://localhost:11434");

            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/tags"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return ConditionEvaluationResult.enabled("Ollama is reachable at " + url);
                }
                return ConditionEvaluationResult.disabled("Ollama returned status " + response.statusCode());
            } catch (Exception e) {
                return ConditionEvaluationResult.disabled(
                        "Ollama is not reachable at " + url + " (" + e.getMessage() + "). Skipping integration tests.");
            }
        }
    }
}

