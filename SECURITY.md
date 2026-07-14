# Security Policy

This project coordinates freshwater fishing-fleet back-office operations,
including catch records and quota data. Treat vulnerabilities as
potentially high impact even when the demo data is synthetic — this is a
safety-critical inland-waters domain.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real vessel, permit, crew, or catch-data exposure
- authorization bypass
- FreshwaterFishingOperationsGovernor bypass
- a path that lets a navigation, gear-operation, or catch-decision op
  auto-commit, or that lets quota exceedance commit
- audit-ledger tampering
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on vessel/crew data, governor enforcement, the coordination-only
  invariant, or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real vessel/crew/catch data outside this repository.
- Run governor and store tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never wire a navigation, gear-operation, or catch-decision effect to
  run without vessel-operator authority — this actor structurally cannot
  propose such ops, and that boundary must never be relaxed.
