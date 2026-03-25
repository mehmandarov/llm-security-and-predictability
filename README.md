# Taming the Chaos: Security and Predictability in LLM-Powered Applications

This repository contains the demo code and examples for the talk **"Taming the Chaos"**. It demonstrates practical patterns for building secure, predictable systems on top of probabilistic LLMs.

## Overview

LLMs are probabilistic by nature — great for creativity, but dangerous for business logic. They can leak sensitive data, obey injected instructions, hallucinate values, and give different answers every time. This project walks through the "5 Stages of LLM Grief," showing how to move from naive implementations to production-hardened resilience — securing inputs and outputs, validating with deterministic code, self-correcting with feedback loops, and building confidence through multi-model consensus. A bonus chapter demonstrates synthetic verification via the "Mirror Test."

## Prerequisites

*   **Java 21+**
*   **Maven**
*   **Ollama** (optional — for end-to-end integration tests)
    ```bash
    # Install: https://ollama.com
    ollama pull gemma3:1b
    ```

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd llm-validation-and-predictability
    ```

2.  **Run the unit tests (no LLM required):**
    The core logic is demonstrated through JUnit tests with mocked models.
    ```bash
    mvn test
    ```

3.  **Run the end-to-end tests (requires Ollama):**
    These hit a real local Ollama instance — great for live demos.
    ```bash
    ollama serve          # in a separate terminal, if not already running
    mvn verify
    ```
    If Ollama is not reachable, integration tests are automatically skipped.

## Project Structure

The code is organized by **Chapters** corresponding to the narrative of the talk:

*   **Chapter 1: The Basics (Naive Implementation)**
    *   `SimpleInvoiceExtractor.java`: A basic LangChain4j service interface.
    *   *Lesson:* Structured output is not validation.
*   **Chapter 2: The Attack (Security & Guardrails)**
    *   `PromptInjectionGuardrail.java`: Blocks malicious inputs via keyword blacklist AND delimiter breakout attempts.
    *   `InputLengthGuardrail.java`: Blocks prompt-stuffing attacks.
    *   `IntentClassifier.java`: A cheap "Bouncer" model that pre-screens malicious intent before calling the expensive model.
    *   `PiiGuardrail.java`: Redacts sensitive data from outputs.
    *   `OutputFormatGuardrail.java`: Rejects non-JSON responses.
    *   `SandwichedInvoiceExtractor.java`: Wraps user input in `<user_input>` / `</user_input>` delimiters (OWASP-recommended).
    *   `SecureInvoiceExtractor.java`: Extraction with injection + PII guardrails wired in.
    *   `CanaryTokenGuardrail.java` + `CanaryInvoiceExtractor.java`: Embeds a secret "canary" token as an injection tripwire.
    *   `FortifiedInvoiceExtractor.java`: All guardrails combined. The full castle.
    *   *Lesson:* Never trust the input; never trust the output. Defense in depth.
*   **Chapter 3: The Hallucination (Deterministic Validation)**
    *   `StrictValidator.java`: Validates schema (Jakarta Beans) and business logic (Math, Dates) *deterministically*.
    *   `OutputNormalizer.java`: Normalizes LLM output (amounts → 2 decimal places, currency → uppercase, strings trimmed) so "close enough" becomes identical. Pure Java, no LLM.
    *   *Lesson:* Trust code, not the model.
*   **Chapter 4: The Bargaining (Self-Correction)**
    *   `CorrectiveExtractor.java`: Feeds validation errors back to the LLM for a second attempt.
    *   *Lesson:* Turn runtime exceptions into successful transactions.
*   **Chapter 5: The Council (Consensus & Determinism)**
    *   `MultiModelConsensus.java`: Queries multiple models and uses majority voting to ensure accuracy.
    *   `StabilityAnalyzer.java`: Runs extraction N times and computes per-field agreement percentages.
    *   *Lesson:* Turn probability into predictability using Seeds, Consensus, and Stability Measurement.
    *   *Bonus:* Seed-based reproducibility tests (`OllamaEndToEndIT.java`) verify whether `temperature=0` + `seed=42` yields identical results.
*   **Bonus: The Grand Finale (The Mirror Test)**
    *   `MirrorVerifier.java`: Performs a round-trip "Reconstruction" (JSON → Text) to detect silent omissions.
    *   *Lesson:* When there is no "Golden Answer," verify the extraction against the original reality.

## Key Technologies

*   **LangChain4j**: For LLM integration and AI Service interfaces.
*   **Ollama**: Local LLM inference for end-to-end tests (gemma3:1b).
*   **Jakarta Validation**: For schema and constraint validation.
*   **JUnit 5 & Mockito**: For unit testing the patterns.
*   **Maven Failsafe**: For integration tests (`mvn verify`).

---
*Note: Common models and shared code live in `com.mehmandarov.llmvalidation.model`.*
