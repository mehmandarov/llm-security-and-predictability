# Long Slide Deck — Taming the Chaos (≈90 min, deep dive)

The full-length version of the talk. This deck walks through the complete narrative: naive extraction, a six-layer security stack, deterministic validation, normalization, tool calling, code execution, MCP integration patterns, self-correction, consensus, stability analysis, a typed-verdict pipeline, goal-oriented contracts, and the Mirror Test.

For the compressed 20-minute version, use `../presentation_short/`.

## When to use this deck

* 60–90 min conference or meetup slot
* Deep technical workshop / brown-bag session
* Audiences that want implementation details, trade-offs, and code references
* Java / backend / platform engineering audiences

## Files

| File | Purpose |
|---|---|
| `00_title.adoc` … `09_thank_you.adoc` | Individual slide files |
| `toc.adoc` | Table of contents slide |
| `.asciidoctorconfig` | Asciidoctor settings |

## Slide map (≈90 min)

| # | File | Section | Time |
|---|---|---|---|
| — | `00_title.adoc` | Title | — |
| — | `00b_about_me.adoc` | About me | 1 min |
| 1 | `01_intro.adoc` | Hook & framing | 4 min |
| 2 | `01b_overview.adoc` | What we'll cover | 2 min |
| 3 | `01c_domain.adoc` | The example: invoices | 2 min |
| 4 | `02_honeymoon.adoc` | Stage 1: Naive extraction | 5 min |
| 5 | `03_attack_overview.adoc` | Stage 2: The attack surface | 3 min |
| 5b | `03b_attack_guardrails.adoc` | Guardrails: blacklist, PII, length | 5 min |
| 5c | `03c_attack_canary.adoc` | Canary trap | 4 min |
| 5d | `03d_attack_output_format.adoc` | Output-format guardrail | 4 min |
| 5e | `03e_attack_sandwiching.adoc` | Delimiter sandwiching | 5 min |
| 5f | `03f_attack_bouncer.adoc` | Intent classifier / Bouncer | 4 min |
| 5g | `03g_attack_fortified.adoc` | The Full Castle — all guardrails combined | 4 min |
| 6 | `04_hallucination.adoc` | Stage 3: Deterministic validation | 5 min |
| 6b | `04b_normalizer.adoc` | Output normalization | 4 min |
| 6c | `04c_tool_calling.adoc` | Function calling / tools | 5 min |
| 6d | `04d_code_execution.adoc` | Code execution / formula evaluation | 5 min |
| 6e | `04e_two_approaches.adoc` | Tool calling vs. code execution | 3 min |
| 6f | `04f_mcp_integration.adoc` | MCP integration and capability scoping | 4 min |
| 6g | `04g_mcp_threats_and_controls.adoc` | MCP threat model and controls | 4 min |
| 7 | `05_bargaining.adoc` | Stage 4: Self-correction | 5 min |
| 8 | `06_council.adoc` | Stage 5: Multi-model consensus | 5 min |
| 8b | `06b_stability.adoc` | Stability analysis | 4 min |
| 8c | `06c_safe_pipeline.adoc` | Typed verdict pipeline ⭐ | 5 min |
| 8d | `06d_goal_oriented_architecture.adoc` | Goal-oriented architecture loop | 4 min |
| 8e | `06e_goal_contracts_and_eval.adoc` | Goal contracts and evaluation | 4 min |
| 9 | `07_mirror.adoc` | Bonus: Mirror Test | 4 min |
| 10 | `08_summary.adoc` | Summary / takeaways | 2 min |
| — | `09_thank_you.adoc` | Thank you / Q&A | — |


## Important note

Code, slides, and text contents are owned by Rustam Mehmandarov and cannot be reproduced without a prior agreement.
