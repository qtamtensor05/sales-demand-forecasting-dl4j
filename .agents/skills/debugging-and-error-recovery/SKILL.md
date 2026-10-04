---
name: debugging-and-error-recovery
description: Diagnose test failures, Maven build errors, runtime exceptions, incorrect forecasts, and unexpected experiment results in this Java 11 DL4J project. Use for root-cause investigation; do not implement a fix unless the user also requests one.
---

# Debugging and error recovery

Apply this workflow only in `sales-demand-forecasting-dl4j`. Preserve evidence and identify the
root cause before proposing changes.

1. Reproduce the smallest observable failure with the existing command, focused JUnit test, or a
   minimal deterministic fixture. Record the exact command, exit code, error, and affected path.
2. Localize the first incorrect boundary: CSV parsing, chronological ordering, preprocessing,
   window generation, tensor construction, model fit/inference, inverse scaling, aggregation, or
   report output. Trace values across that boundary instead of patching the final symptom.
3. Form one falsifiable hypothesis at a time and run the narrowest check that can disprove it.
   Separate code defects from invalid fixtures, missing local data, environment failures, and
   expected stochastic variation.
4. If a fix is authorized, add or strengthen a regression test that fails for the demonstrated
   cause, make the smallest correction, then rerun the focused check and the relevant wider suite.
   If the user requested diagnosis only, stop after reporting evidence and likely remediation.

For preprocessing and evaluation defects, explicitly verify that scalers fit train data only,
targets do not cross chronological split boundaries, lag/rolling features use no future values,
validation selects models without test access, and metrics are compared in original sales units.
For DL4J/ND4J defects, inspect tensor shapes, masks, dtypes, finite values, seeds, checkpoint
selection, and the distinction between direct and autoregressive rollout before changing topology.

Do not explain a metric regression as randomness until the same data/configuration/seed has been
compared. Use the weekly naive baseline as a diagnostic reference, not as proof that neural code is
correct. Keep Java 11 compatibility and do not upgrade dependencies as an incidental fix.

For long training commands, follow `sales-project-progress`: capture full output, avoid reading
epoch logs while a healthy run is active, and inspect the log after completion. Read partial logs
early only for failure or stall diagnosis. Never commit, push, delete data, or broaden the task
without user authorization.
