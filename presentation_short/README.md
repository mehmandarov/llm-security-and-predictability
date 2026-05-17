# Short Slide Deck — Taming the Chaos (≈20 min, mixed audience)

The 20-minute, mixed-audience version of the talk. Same arc as the long deck (`../presentation/`) but the 6-layer security walkthrough and 4-pattern predictability section collapse into single playbook slides, and the deck ends on the typed-verdict capstone that lands equally well with engineers and decision-makers.

## When to use this deck

* 20-min conference slot (lightning, executive track, vendor demo)
* Mixed audience — engineers + product / management together
* Internal alignment talk

For the deep dive (≈90 min), use `../presentation/`.

## Files

| File | Purpose |
|---|---|
| `slides.adoc` | Master file — all slides separated by `// ---- filename.adoc ----` markers |
| `00_title.adoc` … `09_thank_you.adoc` | Individual slide files |
| `toc.adoc` | Auto-generated table of contents |
| `split_slides.py` | Splits `slides.adoc` into individual files + regenerates `toc.adoc` |
| `.asciidoctorconfig` | Asciidoctor settings |

## Workflow

```bash
cd presentation_short
python3 split_slides.py
```

Edit `slides.adoc` (the single source of truth) and re-run the script.

## Slide map (≈20 min)

| # | File | Section | Time |
|---|---|---|---|
| — | `00_title.adoc` | Title | — |
| — | `00b_about_me.adoc` | About me | 30s |
| 1 | `01_intro.adoc` | Hook & framing | 2 min |
| 2 | `01b_overview.adoc` | What we'll cover | 1 min |
| 3 | `01c_domain.adoc` | The example: invoices | 1 min |
| 4 | `02_honeymoon.adoc` | Stage 1: Naive | 2 min |
| 5 | `03_security.adoc` | Stage 2: Security playbook | 4 min |
| 6 | `04_predictability.adoc` | Stage 3: Predictability — Part 1 (validation) | 2 min |
| 6b | `04b_predictability_compute.adoc` | Stage 3: Predictability — Part 2 (function calling vs. code execution) | 2 min |
| 7 | `05_correction_consensus.adoc` | Stage 4: Self-correction (feedback loop) | 1 min |
| 7b | `05b_consensus.adoc` | Stage 5: Multi-model consensus | 1 min |
| 8 | `06_capstone.adoc` | Typed verdict ⭐ | 2 min |
| 9 | `07_mirror.adoc` | Bonus: Mirror Test | 1 min |
| 10 | `08_summary.adoc` | Takeaway | 1 min |
| — | `09_thank_you.adoc` | Thank you / Q&A | — |

Speaker notes: `../TALK_SCRIPT_SHORT.md`.

## Assets

Title image reused from the long deck via `../presentation/assets/title_img.png` — no duplicate binary.
