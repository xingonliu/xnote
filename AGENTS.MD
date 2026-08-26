- Before acting, check the request for false premises, logical gaps, and missing information. Think independently rather than agreeing by default; distinguish verified facts, reasonable inferences, and subjective opinions; verify material claims involving numbers, named individuals, or conclusions against reliable sources whenever practical. If you disagree, say so directly and explain the evidence, risks, and alternative interpretations. Proactively identify overlooked variables, costs, trade-offs, and potential biases.

- At the start of every task in a Git repository, run `git status` and `git fetch --prune origin` before making changes. Inspect the current branch and its upstream state, and protect all existing user work. Never overwrite, revert, reformat, stage, commit, or otherwise alter files outside the task scope.

- If the upstream branch is ahead and the working tree is clean enough for a safe fast-forward, immediately run `git pull --ff-only`. If there are uncommitted changes, branch divergence, conflicts, or any other condition that could overwrite or interfere with user work, stop before writing and report the blocker. Never force-pull, force-overwrite, discard user changes, or use another destructive synchronization method.

- For tasks that change files, complete the implementation, synchronize the relevant documentation, run appropriate tests, and inspect the final diff. Then stage only files within the task scope, create a commit, and push the current branch. Push normally when an upstream already exists; otherwise, use `git push -u origin <branch>`. If the push is rejected, fetch again and synchronize only through a safe, non-destructive method; never force-push.

- Every commit created by Codex must use Chinese and follow this exact format: `codex-<任务简写>（YYYY-MM-DD HH:mm Asia/Shanghai）完成：<具体完成内容>`. Both `<任务简写>` and `<具体完成内容>` must be written in Chinese, the timestamp must reflect the actual commit time in the `Asia/Shanghai` timezone, and the completion summary must describe the delivered result precisely rather than using vague words such as “更新” or “修改”.

- Read-only, explanatory, or diagnostic tasks that produce no file changes do not require an empty commit or push, but the initial Git and remote-state checks are still required.

- Follow only the user’s current, explicitly stated objective. Treat rejected or withdrawn directions as superseded and remove their effects from all project artifacts and responses; deliver only the intended final state, without correction history, exclusionary labels, or repetitive explanations.

- Prefer direct, readable code sufficient for current requirements. Keep functions simple and practical, and introduce defensive logic or abstractions only when justified by concrete requirements or existing usage.

- Unless compatibility is explicitly required or concrete external consumers or production data prevent a coordinated migration, apply breaking changes across the entire repository. Update all callers, tests, fixtures, examples, and documentation, remove the superseded implementation, and do not preserve it through wrappers, aliases, legacy parsing, or migration shims.

- Use precise, self-explanatory names and a clear structure. Comments should explain only non-obvious intent, constraints, edge cases, or trade-offs, and must remain consistent with the code.

- Organize source files in this order: type definitions, inputs and outputs, constants, state and variables, derived values, functions, listeners, and lifecycle hooks. Mark each non-empty section with `// -- Section Name`, keep related content together, and split files by responsibility when the structure becomes unwieldy.
