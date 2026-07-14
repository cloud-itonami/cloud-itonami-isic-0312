# cloud-itonami-isic-0312

Open Business Blueprint for **ISIC Rev.5 0312: Freshwater fishing** — a freshwater (river/lake/inland-waters) fishing-fleet operations coordination actor.

## Overview

This actor supports an inland fishing fleet's back-office coordination workflow:

- **Catch-record logging** — species, quantity, quota-tracking data (administrative, not a fishing decision)
- **Vessel maintenance scheduling** — propose engine/gear maintenance
- **Safety concern flagging** — surface a vessel-safety, water-level (flash flood / rapid rise), or weather concern (always escalates to the vessel operator/crew)
- **Supply ordering** — fuel/gear/provisions procurement proposals

## What this actor does NOT do

This is a safety-critical inland-waters domain (small craft on rivers and lakes). This actor is **coordination only**. It never:

- **Navigates the vessel** — no course, heading, waypoint, or autopilot commands
- **Operates fishing gear** — no net deployment, trap setting, or gear hauling
- **Makes catch decisions** — no target-species, quota-allocation, or where/when-to-fish decisions
- **Commands the vessel** in any way (engine, throttle, propulsion)

Vessel navigation, gear operation, and catch decisions remain the **vessel operator's exclusive human authority on the water**, always. The governor structurally and permanently blocks any proposal touching these — there is no human-approval path that can override this block.

Unlike marine (EEZ) fishing, freshwater fishing has no maritime-zone or flag-state concept — a vessel operates on a single named inland water body (a river or lake) under one jurisdiction's permit/quota regime. There is no per-zone quota split.

## Governance Model

All proposals carry `:effect :propose` — this actor is **coordination only**. Every op is drawn from a closed allowlist (`freshwater-fishing.governor/op->effect`); the advisor cannot legitimately propose anything outside it.

Hard blocks (no human override):
1. **Vessel/permit verification** — vessel must be registered and `:status :active` before any action
2. **Effect integrity** — proposal effect must match the request operation
3. **Coordination-only effects** — all effects are `:propose`
4. **Forbidden fields** — vessel-id, permit-number, vessel-status cannot be touched
5. **Blocked operations** — navigation / vessel-command / catch-decision proposals rejected outright
6. **Quota exceedance** — a catch record that would push cumulative landed weight past the permit quota is a hard, permanent block (a regulatory compliance breach, not a judgment call)

Escalation triggers (human sign-off required):
- **Safety concerns** always escalate immediately
- **High-cost supply orders** (>= 10,000 units) escalate
- **Low advisor confidence** (< 0.6) escalates

## Architecture

```
Request → Advisor (LLM) → Proposal
            ↓
         Governor (independent censor)
            ↓
         Decide (hold | escalate | commit)
            ↓
         Store (persistent audit ledger)
```

All modules are portable `.cljc` (ClojureScript-first, compatible with JVM/nbb).

## Building & Testing

```bash
# Install (workspace already has langgraph-clj at ../../kotoba-lang/langgraph)
clojure -M:test

# Run demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Files

- `blueprint.edn` — open business blueprint metadata
- `src/freshwater_fishing/` — core actor modules
  - `governor.cljc` — compliance rules and hard blocks
  - `store.cljc` — persistent store protocol + in-memory impl
  - `llm_advisor.cljc` — advisor interface (mock or real LLM)
  - `operation.cljc` — langgraph-clj state machine
  - `sim.cljc` — demo simulation
- `test/freshwater_fishing/` — contract tests for governance and store

## License

AGPL-3.0-or-later

## Contributing

This actor is part of the cloud-itonami open business blueprint fleet. See CONTRIBUTING.md.
