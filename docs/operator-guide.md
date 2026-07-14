# Operator Guide

## Getting Started

### Prerequisites
- Clojure CLI (latest)
- Java 11+ (JVM test/dev path) — the actor's own `src/` is portable
  `.cljc` with no JVM-only interop
- A freshwater fishing vessel or fleet to manage

### Installation

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-0312.git
cd cloud-itonami-isic-0312
clojure -M:test
```

### Quick Demo

```bash
clojure -M:dev:run
```

This prints the demo vessel registration and a summary of the active
governance rules (see `src/freshwater_fishing/sim.cljc`). It does not yet
drive a full request through the compiled `freshwater-fishing.operation`
graph end-to-end — that wiring is a natural next extension, following the
same shape as `cloud-itonami-isic-0321`'s `aquaculture.sim`.

## Core API

```clojure
(require '[freshwater-fishing.store :as store]
         '[freshwater-fishing.operation :as operation])

;; Register a vessel (fixture-style; swap for a persistent Store impl in production)
(def st (store/mem-store
          {:fixtures {"vessel-001"
                      {:vessel-id "vessel-001"
                       :name "River Hope"
                       :status :active
                       :water-body "Lake Biwa"
                       :permit-number "PERMIT-INLAND-001-2026"
                       :quota-kg 8000
                       :landed-kg 0}}}))

;; Build the OperationActor graph bound to this store
(def actor (operation/build st))
```

### Direct store operations (administrative — not fishing decisions)

```clojure
;; Log a catch record (accrues against the vessel's quota)
(store/log-catch! st "vessel-001" {:species "rainbow-trout" :quantity-kg 45})

;; Schedule maintenance
(store/schedule-maintenance! st "vessel-001" {:type :outboard-motor-service :scheduled-date "2026-08-20"})

;; Flag a safety concern -- always escalates to the vessel operator/crew
(store/flag-safety-concern! st "vessel-001" {:concern "rising-water-level"})

;; Order supplies
(store/order-supply! st "vessel-001" {:item "fuel" :quantity-l 200 :estimated-cost 600})

;; Read the audit trail
(store/audit-log st "vessel-001")
```

### Governor checks (what actually blocks/escalates a proposal)

```clojure
(require '[freshwater-fishing.governor :as governor])

(governor/check {:op :log-catch-record :vessel-id "vessel-001"}
                 nil
                 {:effect :propose :value {:quantity-kg 500} :confidence 0.95}
                 st)
;; => {:ok? true :violations [] :soft-flags [] :confidence 0.95 :hard? false :escalate? false}
```

## Safety Gates

### Hard Blocks (Never Override)

These are PERMANENTLY REJECTED, structurally, with no human-approval override path:

- **Vessel/permit not verified** (`:vessel-not-found` / `:vessel-inactive`)
- **Effect mismatch or non-`:propose` effect** (`:effect-mismatch` / `:non-propose-effect`)
- **Forbidden field modification** — `:vessel-id`, `:permit-number`, `:vessel-status` (`:forbidden-field`)
- **Navigation / vessel-command / catch-decision ops** — `:navigate-vessel`, `:command-vessel`, `:decide-catch` (`:blocked-operation`)
- **Quota exceedance** — a catch record that would push cumulative landed weight past the vessel's permit quota (`:quota-exceedance`)

If the advisor generates a blocked op, it's a sign the LLM has been
misconfigured — those ops are outside the closed allowlist in
`freshwater-fishing.governor/op->effect` and should never be legitimately
proposed.

### Soft Gates (Escalate to Human, Not Rejected)

- **Safety concern** (`:flag-safety-concern`) — always escalates immediately
- **High-cost supply order** (>= 10,000 units estimated cost) — escalates
- **Low advisor confidence** (< 0.6) — escalates

## Auditing

Every catch record, maintenance schedule, safety flag, and supply order
is logged via `freshwater-fishing.store/audit-log`:

```clojure
(store/audit-log st "vessel-001")
;; => [{:t <ms> :type :catch-logged :record {...}}
;;     {:t <ms> :type :safety-concern-flagged :concern {...} :escalated true}
;;     ...]
```

## Troubleshooting

### "Proposal was rejected with :quota-exceedance?"

The catch record's `:quantity-kg` plus the vessel's current `:landed-kg`
exceeds `:quota-kg`. This is a hard, permanent block by design — it is a
regulatory compliance breach, not a judgment call, and cannot be
overridden by human sign-off. There is no maritime-zone split here to
reallocate against.

### "Proposal was rejected with :blocked-operation?"

The `:op` requested is `:navigate-vessel`, `:command-vessel`, or
`:decide-catch` — all vessel-operator-exclusive authority, permanently
outside this actor's legitimate scope.

### "Vessel not verified?"

Ensure the vessel is registered in the store with `:status :active`:

```clojure
(store/vessel st "vessel-001")
;; Should return the vessel record with :status :active
```

## Next Steps

- Swap `freshwater-fishing.store/MemStore` for a Datomic-backed `Store`
  impl for production persistence
- Wire a real LLM advisor via the `freshwater-fishing.llm-advisor/Advisor`
  protocol
- Extend `freshwater-fishing.sim` to drive a full request through the
  compiled `freshwater-fishing.operation` graph, following
  `cloud-itonami-isic-0321`'s pattern
