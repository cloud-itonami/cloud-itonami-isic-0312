# Business Model

## The Problem

Inland (river/lake) fishing fleets operate under jurisdictional
regulations:
- Catch logging and quota compliance
- Vessel maintenance requirements
- Supply chain coordination
- Crew safety reporting (including water-level and weather conditions)

Today's fleets use paper logs, email spreadsheets, and verbal handoffs, creating:
- Audit trail gaps
- Delayed safety escalations
- Quota compliance risk
- Supply shortages due to poor coordination

## The Solution

This actor provides an open-source fleet operations coordinator that any qualified freshwater fishing operator can fork, deploy, and customize:

1. **Vessel & Permit Registry** — authoritative registry of vessels, permits, and quota allocations (`freshwater-fishing.store/vessel`)
2. **Catch Logging** — structured catch records with species and quantity, accrued against permit quota (`freshwater-fishing.store/log-catch!`)
3. **Maintenance Scheduling** — proposals for preventive and reactive maintenance (`freshwater-fishing.store/schedule-maintenance!`)
4. **Supply Ordering** — procurement proposals for fuel, gear, provisions (`freshwater-fishing.store/order-supply!`)
5. **Safety Escalation** — immediate notification of vessel-safety, water-level, or weather concerns to crew/operator (`freshwater-fishing.store/flag-safety-concern!`)

This actor is **coordination only** — it never navigates the vessel, operates fishing gear, or makes catch decisions. See the README and ADR-0001 for the full scope boundary.

## Revenue Models for Implementers

Freshwater fishing fleet operators who deploy this actor can monetize it:

1. **Fleet Management SaaS** — host the actor on cloud infrastructure, provide a UI, charge per vessel
2. **Compliance Auditing** — audit fleets' catch logs and quota accrual, certify compliance to regulators
3. **Supply Chain Integration** — connect to fuel/gear suppliers, earn commission on orders
4. **Insurance/Risk Management** — bundle with vessel insurance, use the audit ledger for claims
5. **Consulting** — help fleets adapt the actor to local regulations and quota regimes

## Technical Deployment Paths

### Self-Hosted (fishing fleet)
- Clone repo
- Run on the fleet's own infrastructure
- Integrate with existing vessel systems via a thin adapter that calls
  `freshwater-fishing.operation/build` with the fleet's own `Store`
  implementation
- Maintain the audit ledger locally or on Datomic (swap
  `freshwater-fishing.store`'s `MemStore` for a Datomic-backed
  implementation of the same `Store` protocol)

### Managed Deployment (service provider)
- Fork repo
- Add a multi-tenant `Store` implementation (Datomic per fleet, or
  multi-tenant Datomic)
- Deploy on Kubernetes/cloud
- Offer a white-label UI
- Provide compliance certification

### Hybrid (fleet + service provider)
- Vessel systems run the actor locally
- Sync the audit ledger to a central compliance auditor periodically
- Central auditor provides regulatory reporting and appeals

## Open Source + Business

This repo is AGPL-3.0-or-later, which means:
- Fishing fleets using the actor OWN their data and audit logs
- Service providers who modify the actor must open-source their changes
- Commercial deployments must contribute improvements back to the community

This "copyleft" model ensures the fishing community benefits from improvements while allowing businesses to build on top of it.

## Expected Impact

1. **Compliance**: automated, quota-aware catch logging reduces regulatory violations
2. **Safety**: immediate escalation of vessel-safety, water-level, and weather concerns supports faster response on the water
3. **Sustainability**: hard quota enforcement (not a soft/overridable check) helps prevent overfishing of rivers and lakes
4. **Efficiency**: automated maintenance and supply-ordering proposals reduce fleet downtime
5. **Transparency**: append-only audit ledger provides evidence for claims/disputes

## Honest Scope Note

This actor does not itself provide jurisdiction-specific regulatory-fact
lookup, citation validation, or a compliance-report generator — those
would be legitimate future extensions, but are not implemented in this
repository today. Do not assume they exist; check `src/freshwater_fishing/`
for the actual module list.
