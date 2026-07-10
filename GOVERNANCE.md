# Governance

`cloud-itonami-isic-6209` is an OSS open-business blueprint. Governance
covers both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- TicketRouter-LLM cannot directly route, disclose or resolve a dispute.
- TicketGovernor remains independent of the advisor.
- hard governor violations (access-tier-clearance-gate,
  security-incident-misrouting-gate, source-provenance-gate,
  licensed-disclosure) cannot be overridden by human approval.
- a dispute request never auto-resolves, at any rollout phase.
- a ticket close to SLA breach always reaches a human, regardless of
  confidence.
- every commit, hold and disclosure event is auditable.
- no schema field exists for payroll, timesheet approval, or
  employer-of-record status.

## Decision Records

Architecture decisions live in `docs/adr/`.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and
data-flow review.

Certified operators can lose certification for:

- bypassing governor checks
- routing a security-incident ticket to an uncertified technician
- routing an elevated-access ticket to an under-cleared technician
- disclosing data to an uncontracted party
- misrepresenting certification status
