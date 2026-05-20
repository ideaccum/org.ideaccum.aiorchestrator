# AGENTS.md

タスク実行においては、@rules/RULE.mdを必ず参照し、タスク実行時の行動標準を準拠する。
また、@PROJECT.mdが存在し、内容記載がある場合は、その内容を理解したうえでタスク実行を行う。

## memory

コンテキスト圧迫、コンテキスト溢れ、他セッションとの記憶共有に@memory/memory.mdで管理する。
- タスク実行後に@memory/memory.mdに必要な内容を記録、更新する。
- @memory/memory.md自体も肥大化することを防止したいので、@memory/memory.mdは目次の位置づけとして、カテゴリごとに@memory/memory～.mdに分割して管理する。
- 自分自身が知らない情報が必要な場合、まず@memory/memory.mdに情報が存在しないか確認する。

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read @graphify-out/GRAPH_REPORT.md for god nodes and community structure.
- If @graphify-out/wiki/index.md exists, navigate it instead of reading raw files.
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files.
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost).
