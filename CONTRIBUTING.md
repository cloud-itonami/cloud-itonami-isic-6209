# Contributing

`cloud-itonami-isic-6209` accepts contributions to the OSS actor, governor
tests, documentation, examples and open business blueprint.

## Development

```bash
kbb -M:dev:test
kbb -M:lint
```

Keep changes small and include tests for governor, audit, store or
disclosure behavior.

## Rules

- Do not commit real client tickets, real technician PII, or credentials.
- Keep production routing and disclosures behind TicketGovernor.
- Treat every new ticket category as high-risk: add tests for
  access-tier-clearance-gate, security-incident-misrouting-gate,
  licensed-disclosure, confidence floor and audit logging.
- Never fabricate a certification-catalog entry to expand apparent
  coverage.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
