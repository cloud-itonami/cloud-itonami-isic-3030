# cloud-itonami-3030

Open Business Blueprint for **ISIC Rev.5 3030**: aircraft and aerospace manufacturing enablement — airframe assembly, composite layup and verification.

This repository designs a forkable OSS business for aircraft and aerospace manufacturing enablement:
run by a qualified operator so a community keeps its own operating records
instead of renting a closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here an assembly and inspection robot performs fastening, layup and NDT on airframe structures under an actor that proposes
actions and an independent **Aerospace Manufacturing Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
handling flight-critical structures, composites and large assemblies) require human sign-off.

## Core Contract

```text
intake + identity + cae records
        |
        v
Advisor -> Aerospace Manufacturing Governor -> proceed, hold, or human approval
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `3030`). Required capabilities:

- `:robotics`
- `:cae`
- `:cfd`
- `:dmn`
- `:bpmn`
- `:audit-ledger`
- `:optimization`

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
