---
name: code-review-and-quality
description: Review diffs, files, or proposed changes in this Java 11 Maven/DL4J forecasting project for correctness, leakage, tests, maintainability, security, and performance. Use when the user asks for review or pre-merge assessment; report findings without modifying code unless fixes are requested.
---

# Code review and quality

Review the requested diff or files against repository behavior and evidence. Lead with actionable
findings ordered by severity; include file and line references, the failure mode, why it matters,
and the smallest credible remediation. Do not manufacture findings to fill categories.

Prioritize these project-specific risks:

- future leakage through scaler fitting, feature computation, window construction, model selection,
  or accidental test-set access;
- changed chronological boundaries, horizon/date alignment, overlapping-window interpretation, or
  aggregation that lets high-sales series dominate a claimed macro metric;
- incorrect ND4J shapes, masks, output time steps, inverse transforms, autoregressive history
  updates, non-finite values, and seed/checkpoint handling;
- misleading comparisons caused by different samples, seeds, units, horizons, or validation rules;
- missing edge/error-path tests, brittle floating-point assertions, or tests that require the
  private dataset or the full multi-hour experiment;
- Java features newer than 11, unmanaged dependency changes, unsafe CSV/path handling, swallowed
  exceptions, resource leaks, excessive allocations, and avoidable repeated training/work.

Check whether public interfaces and reports remain understandable, whether names reflect Direct vs
Autoregressive behavior, and whether documentation matches observed behavior. Preserve scope: a
review request does not authorize edits, dependency upgrades, commits, pushes, or new experiments.

Use fresh commands only when they materially validate the review. Prefer focused tests before the
full Maven suite; do not start the two-hour training run by default. If a long run is necessary and
authorized, use the quiet log workflow from `sales-project-progress`.

If no material issue is found, say so explicitly and list residual risks or untested areas. Separate
verified defects from questions and suggestions; do not claim tests passed unless they were run in
the current request and their exit codes were checked.
