# Contributing

`cloud-itonami-isic-0312` accepts contributions to the OSS actor, governor
tests, documentation, and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, store, or operation
behavior.

## Rules

- Do not commit real vessel, permit, crew, or catch data.
- Keep the coordination-only invariant intact — never add a governor
  effect other than `:propose`, and never remove a rule from
  `freshwater-fishing.governor`'s hard-check list.
- Never add `:navigate-vessel`, `:command-vessel`, `:decide-catch`, or any
  equivalent navigation/gear/catch-decision op to the allowlist
  (`freshwater-fishing.governor/op->effect`) — those remain the vessel
  operator's exclusive human authority on the water, permanently, by
  design.
- Treat this as a safety-critical inland-waters domain: add tests for any
  new hard block or escalation rule with every change.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested (paste the `clojure -M:test` output)
- whether operator or compliance docs need updates
