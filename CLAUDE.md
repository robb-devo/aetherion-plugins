# Aetherion plugins — start here

- Current state (modules, live builds, content, where code lives): `docs/PROJECT_STATE.md`
- Open beta work, by priority: `docs/BETA_AUDIT_2026-10-19.md`
- Ops, hosting and access details are private: `aetherion-ops/docs/` on Robb's PC. Never commit
  hosts, keys, passwords, ports or server ids here, even though the repo is private.

Rules that keep breaking things when ignored:
- `main` equals live. Build and deploy only from the ship worktree, then commit, push and merge.
- Never overwrite live plugin YAMLs; patch them.
- After deploying a jar, restart every server that loads it, and keep exactly one jar file per plugin per server.
- Resource-pack swap: replace the served zip **and** update `resource-pack-sha1` on every backend.
- Read `docs/PROJECT_STATE.md` before rescanning the repo or the servers; update it when state changes.
- Agent prompts live in `docs/prompts/` and are history, not specs.
