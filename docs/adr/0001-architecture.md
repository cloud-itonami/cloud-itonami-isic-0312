# ADR-0001: Freshwater Fishing Fleet Operations Coordination Actor Architecture

## Status
Accepted.

## Context

Inland (river/lake) fishing fleets require coordination of back-office
operations — catch-record/quota logging, maintenance scheduling,
safety-concern escalation, and supply ordering. These are primarily
back-office coordination tasks. However, critical on-the-water decisions
(vessel navigation, fishing-gear operation, catch decisions) must remain
with the vessel operator — the sole human authority on the water.

Unlike marine (EEZ) fishing, freshwater fishing has no maritime-zone or
flag-state concept: a vessel is registered against a single named inland
water body (a river or lake) under one jurisdiction's permit/quota regime.
There is no per-zone quota split to model.

The governance model must separate:
- **Coordination proposals** (advisory/proposal-stage) — which the LLM
  can generate
- **Human-exclusive authority** (navigation, gear operation, catch
  decisions) — which are permanently blocked, structurally, with no
  human-approval override path
- **Escalation points** (safety concerns, high-cost supplies, low
  confidence) — which require human review before commit

## Decision

Implement a freshwater fishing fleet operations actor using the
cloud-itonami pattern (mirroring `cloud-itonami-isic-0311`, Marine
fishing, adapted for inland waters):

1. **Governor** (`freshwater-fishing.governor`): independent compliance
   layer that enforces hard rules
   - Vessel/permit must be verified and `:status :active` before any
     action
   - All effects are `:propose` only (coordination, not direct
     actuation)
   - A closed op allowlist (`op->effect`) is the only legitimate set of
     proposals; ops outside it (navigation, vessel-command, catch
     decisions) are hard-blocked, permanently
   - A catch record whose quantity would push cumulative landed weight
     past the vessel's permit quota is a hard, permanent block (a
     regulatory compliance breach, not a judgment call — never a soft
     escalation). There is no maritime-zone split — one permit, one
     quota, one inland water body.
   - Safety concerns (vessel-safety, water-level, weather) and high-cost
     supplies always escalate to a human

2. **Store Protocol** (`freshwater-fishing.store`): abstracted data layer
   - In-memory implementation for development/testing
   - Future swap path to Datomic/kotoba-server
   - Append-only audit ledger for all operations
   - Tracks cumulative landed weight per vessel for quota enforcement

3. **LLM Advisor** (`freshwater-fishing.llm-advisor`): proposal generator
   - Mock implementation for testing
   - Real LLM injection point via protocol
   - No external service calls in the base module
   - Can only ever generate ops within the governor's closed allowlist —
     it has no notion of navigation, gear operation, or catch decisions

4. **State Machine** (`freshwater-fishing.operation`): langgraph-clj graph
   - Linear flow: request → advise → govern → decide → commit | hold |
     escalate
   - Checkpointed operation (one graph run = one auditable operation)
   - No unbounded loops

## Operations

**Allowed proposals (closed allowlist, all `:effect :propose`):**
- `:log-catch-record` — catch/quota data logging (administrative, not a
  fishing decision)
- `:schedule-vessel-maintenance` — maintenance scheduling proposal
- `:flag-safety-concern` — surface a vessel-safety/water-level/weather
  concern, ALWAYS escalates
- `:order-supplies` — fuel/gear/provisions procurement proposal

**Blocked permanently (not in the allowlist; hard-rejected if proposed):**
- `:navigate-vessel` — vessel operator's exclusive authority
- `:command-vessel` — vessel operator's exclusive authority
- `:decide-catch` — vessel operator's exclusive authority

**Hard Governor Rules (no human override):**
1. Vessel verification: `:vessel-id` must exist and have `:status
   :active`
2. Effect integrity: proposal `:effect` must match `:op`
3. Coordination-only: all effects must be `:propose`
4. Forbidden fields: `:vessel-id`, `:permit-number`, `:vessel-status`
   never modifiable
5. Blocked operations: `:navigate-vessel`, `:command-vessel`,
   `:decide-catch` rejected outright
6. Quota exceedance: a `:log-catch-record` proposal whose
   `:quantity-kg` would push cumulative `:landed-kg` past `:quota-kg` is
   rejected outright, permanently — no human sign-off can override this

**Escalation Triggers (human review required, not a rejection):**
- Safety-concern flags (always)
- Supply orders >= 10,000 units (default threshold)
- Advisor confidence < 0.6

## What this actor does NOT do

This actor has **no navigation authority, no fishing-gear-operation
authority, and no catch-decision authority**. It never sets a course or
heading, never operates or directs fishing gear, never decides
what/where/when to fish or how quota is allocated, and never commands the
vessel (engine, throttle, propulsion). Those decisions are the vessel
operator's exclusive human authority on the water, permanently, with no
override path through this actor.

## Consequences

- **Coordination safety**: the LLM cannot propose navigation, gear
  operation, or catch decisions — those ops simply do not exist in its
  legitimate vocabulary, and the governor structurally rejects them if
  generated anyway
- **Regulatory safety**: quota exceedance is caught and blocked before
  it can be logged as a committed catch record
- **Audit transparency**: all operations are logged with proposer,
  governor verdict, and human decisions
- **Vessel-operator authority preservation**: the vessel operator retains
  exclusive authority over navigation, gear, and catch decisions
- **Extensibility**: store protocol allows future swap to persistent
  database
- **Testability**: pure governor checks and store protocol enable
  comprehensive contract tests

## Testing

- Governor contract tests: all hard rules (vessel verification, effect
  integrity, propose-only, forbidden fields, blocked operations, quota
  exceedance) and soft escalations (safety concern, high-cost supply,
  low confidence)
- Store contract tests: vessel lookup, quota accrual, audit trail,
  maintenance scheduling, supply ordering
- Run via `clojure -M:test`; see the repository's CI workflow
  (`.github/workflows/test.yml`) for the exact invocation
