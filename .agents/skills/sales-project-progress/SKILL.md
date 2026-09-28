---
name: sales-project-progress
description: Update Vietnamese implementation history, current state, and request logs with a suggested commit title after completing work in the sales-demand-forecasting-dl4j coursework repository. Applies to code, documentation, configuration, and investigation requests in this project.
---

# Sales project progress

Apply only to the sales-demand-forecasting-dl4j project. The repository copy of this skill
is authoritative; a personal installation is only for discovery. Resolve paths below
relative to the project root, not the installed skill directory.

Before finalizing a completed user request:

1. Read `docs/README.md` and the relevant entries in `docs/implementation-history.md`,
   `docs/current-state.md`, and `docs/change-log.md`. Inspect the affected code/diff and actual
   validation output; preserve unrelated changes and earlier history.
2. Append one entry to `docs/change-log.md` with an increasing ID, local date, user objective,
   reason, concrete changes or findings, affected files, observed results, checks and their
   outcomes, remaining limitations, and one suggested commit title. Do not invent old dates,
   test runs, metrics, commit hashes, or completed features.
3. For changed behavior or technical decisions, append an implementation step and update
   current state. Correct stale README usage when necessary. For investigations without code
   changes, record the diagnosis and evidence; say explicitly that code was unchanged.
4. Check Markdown links and `git diff --check`. Run functional tests only when relevant to
   the actual change; distinguish prior test evidence from checks performed this request.
5. In the final answer link the updated documentation, summarize results/limitations,
   and provide a concise English Conventional Commit title, e.g.
   `feat: add train-only scaling and chronological windows` or
   `docs: record commit check investigation`.

Use Vietnamese prose in project docs. Keep rationale tied to the actual requirement.
Maintain append-only historical meaning: later changes can supersede earlier behavior;
do not rewrite historical results as if the new behavior always existed.
For an unfinished request, label the outcome incomplete rather than claiming completion.

For a request solely asking for a commit title, reuse/update the relevant log entry instead
of generating a new implementation step. Log updates themselves do not trigger recursive
entries. One completed request needs one log entry, not one entry per tool call.

Suggesting a title does not authorize `git add`, commit, push, dependency upgrades, or
unrelated implementation. This skill is an agent workflow, not a background task or Git hook.
