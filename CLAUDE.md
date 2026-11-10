# Aetherion plugins — start here

- Current state (modules, live builds, content, where code lives): `docs/PROJECT_STATE.md`
- Code health and cleanup plan (what to fix under the hood, in which order): `docs/CODE_AUDIT_2026-11-10.md`
- Older beta work list: `docs/BETA_AUDIT_2026-10-19.md`
- Ops, hosting and access details are private: `aetherion-ops/docs/` on Robb's PC. Never commit
  hosts, keys, passwords, ports or server ids here, even though the repo is private.

Rules that keep breaking things when ignored:
- `main` equals live. Merge the PR first, then build and deploy from the clean clone of `main`
  (`IdeaProjects/_deploy/aetherion-plugins`); never from a work branch or the stale root checkout.
- Never overwrite live plugin YAMLs; patch them.
- After deploying a jar, restart every server that loads it, and keep exactly one jar file per plugin per server.
- Resource-pack swap: build it with `AetherionItems/tools/build_served_pack.py` from the live zip and replace the
  served zip. Nothing else: the proxy (AetherionProxy) re-reads the hash every minute and sends it; backends
  advertise no pack, keep their `resource-pack=` empty.
- Read `docs/PROJECT_STATE.md` before rescanning the repo or the servers; update it when state changes.
- Agent prompts live in `docs/prompts/` and are history, not specs.
