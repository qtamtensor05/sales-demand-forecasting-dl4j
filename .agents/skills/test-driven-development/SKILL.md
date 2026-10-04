---
name: test-driven-development
description: Implement behavior changes and bug fixes in this Java 11 Maven/DL4J project with focused JUnit regression tests. Use for production-code changes; skip for documentation-only work, investigations, or requests that do not authorize implementation.
---

# Test-driven development

Use a red-green-refactor loop sized to the requested behavior:

1. Define one observable acceptance condition and choose the narrowest existing test class or a new
   focused JUnit 5 test. Reuse small synthetic series instead of the private full dataset.
2. Run the focused test and confirm it fails for the intended reason. A compile error, unrelated
   failure, or invalid fixture is not a useful red state.
3. Make the smallest production change that satisfies the condition while remaining compatible
   with Java 11. Do not weaken assertions, special-case test data, or change unrelated behavior.
4. Rerun the focused test to green, then refactor without changing behavior. Run the relevant test
   class again and finish with `mvn test` when the scope or risk justifies the full suite.

Tests that touch preprocessing must protect train-only normalization, chronological splits, full
target containment inside a split, causal lag/rolling features, and stable window counts/dates.
Tests that touch forecasting should check tensor/mask shapes, finite predictions, deterministic
seed plumbing where supported, inverse-transformed units, horizon ordering, and validation-only
selection. Prefer properties and tolerances over exact floating-point weights or long training
outcomes.

Keep unit and smoke tests fast. Do not turn the default multi-series 120-fit experiment into a test.
Run a real training experiment only when the requested behavior cannot be validated otherwise;
when it is needed, preserve the existing train/validation/test policy and follow the quiet log
workflow in `sales-project-progress`.

Report the initial failing check, the final passing checks, and any behavior not covered. Do not
claim success from an earlier run, and do not commit or push unless the user explicitly asks.
