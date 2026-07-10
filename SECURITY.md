# Security Policy

This project handles client ticket data and technician access-tier/
certification claims. Treat vulnerabilities as potentially high impact even
when the demo data is synthetic — a misrouted security-incident ticket or a
falsely-cleared technician has direct security consequences.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- TicketGovernor bypass (access-tier-clearance-gate,
  security-incident-misrouting-gate, licensed-disclosure)
- audit-ledger tampering
- over-disclosure beyond a subscriber contract's tier
- tenant isolation failures
- routing of an elevated/security-incident ticket without the required
  clearance/certification check

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

## Production Guidance

- Store secrets outside Git.
- Run governor tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for dispatchers and service accounts.
- Alert on any access-tier-clearance-gate or security-incident-
  misrouting-gate HOLD spike.
