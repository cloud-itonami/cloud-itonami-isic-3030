# Security Policy

This project handles aircraft and aerospace manufacturing workflows,
including assembly, NDT-inspection and airworthiness-certification
records. Treat vulnerabilities as potentially high impact even when the
demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real assembly, inspection or airworthiness-evidence data exposure
- authorization bypass
- Aerospace Manufacturing Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on assembly/airworthiness data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real assembly/manufacturing and airworthiness data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
