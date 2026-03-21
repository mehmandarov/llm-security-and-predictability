# Taming the Chaos: Validation and Determinism in LLM-Powered Applications

This repository contains the demo code and examples for the talk **"Taming the Chaos"**. It demonstrates practical patterns for building reliable, deterministic systems on top of probabilistic LLMs.

## Overview

LLMs are probabilistic by nature—great for creativity, but challenging for business logic. This project walks through the "5 Stages of LLM Grief," showing how to move from naive implementations to production-hardened resilience.

## Prerequisites

*   **Java 17+**
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
    *   `PromptInjectionGuardrail.java`: Blocks malicious inputs.
    *   `PiiGuardrail.java`: Redacts sensitive data from outputs.
    *   *Lesson:* Never trust the input; never trust the output.
*   **Chapter 3: The Hallucination (Deterministic Validation)**
    *   `StrictValidator.java`: Validates schema (Jakarta Beans) and business logic (Math, Dates) *deterministically*.
    *   *Lesson:* Trust code, not the model.
*   **Chapter 4: The Bargaining (Self-Correction)**
    *   `CorrectiveExtractor.java`: Feeds validation errors back to the LLM for a second attempt.
    *   *Lesson:* Turn runtime exceptions into successful transactions.
*   **Chapter 5: The Council (Consensus)**
    *   `MultiModelConsensus.java`: Queries multiple models and uses majority voting to ensure accuracy.
    *   *Lesson:* Democracy for AI reduces individual model bias/error.

## Key Technologies

*   **LangChain4j**: For LLM integration and AI Service interfaces.
*   **Ollama**: Local LLM inference for end-to-end tests (gemma3:1b).
*   **Jakarta Validation**: For schema and constraint validation.
*   **JUnit 5 & Mockito**: For unit testing the patterns.
*   **Maven Failsafe**: For integration tests (`mvn verify`).

---
*Note: Common models and shared code live in `com.mehmandarov.llmvalidation.model`.*
