# cloud-itonami-isic-3030

Open Business Blueprint for **ISIC Rev.5 3030**: aircraft and aerospace
manufacturing enablement -- airframe assembly, composite layup and
verification.

This repository publishes an aerospace-manufacturing actor -- assembly
intake, requirements verification, NDT-defect screening, assembly-
action dispatch and airworthiness-evidence issuance -- as an OSS
business that any qualified manufacturer can fork, deploy, run,
improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190)) --
the FIRST manufacturing vertical in this fleet (every prior actor has
been a human-services, leisure, financial-services or infrastructure/
utility domain). Here it is **Aerospace Advisor ⊣ Aerospace
Manufacturing Governor**.

> **Why an actor layer at all?** An LLM is great at drafting an
> assembly-intake summary, normalizing records, and checking whether
> an assembly's own measured dimensional tolerance actually stays
> within its own recorded spec bounds -- but it has **no notion of
> which jurisdiction's airworthiness-certification requirements are
> official, no license to dispatch a real robot assembly action on a
> flight-critical structure or issue real airworthiness evidence, and
> no way to know on its own whether an NDT-detected defect against the
> assembly has actually stayed unresolved**. Letting it dispatch an
> assembly action or issue airworthiness evidence directly invites
> fabricated certification citations, an out-of-spec assembly being
> physically fastened together, and an unresolved NDT defect being
> quietly certified as flight-worthy -- and liability, and public-
> safety risk, for whoever runs it. This project seals the Aerospace
> Advisor into a single node and wraps it with an independent
> **Aerospace Manufacturing Governor**, a human **approval workflow**,
> and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers assembly intake through requirements verification,
NDT-defect screening, assembly-action dispatch and airworthiness-
evidence issuance. It does **not**, by itself, hold any type-
certification or production-certificate required to manufacture
aircraft/aerospace structures in a given jurisdiction, and it does not
claim to. It also does **not** model a real fab/assembly-line control
system, a real robot motion-planning/force-control stack, or a full
CAE/CFD simulation engine -- no direct hardware dispatch protocol, no
finite-element solver (see `aerospace.facts`'s own docstring for the
honest simplification this makes: a starting catalog of airworthiness-
certification authorities, not a survey of every jurisdiction's
certification-specification variant). Whoever deploys and operates a
live instance (a certificated aerospace manufacturer) supplies any
jurisdiction-specific type-certification, the real manufacturing/
robotics engineering and the real CAE/CFD/NDT tooling integrations,
and bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that manufacturer
does not have to build the compliance layer from scratch for every new
program.

### Actuation

**Dispatching a real robot assembly action on a flight-critical
structure or issuing real airworthiness evidence is never autonomous,
at any phase, by construction.** Two independent layers enforce this
(`aerospace.governor`'s `:actuation/dispatch-assembly`/`:actuation/
issue-airworthiness-evidence` high-stakes gate and `aerospace.phase`'s
phase table, which never puts `:actuation/dispatch-assembly`/
`:actuation/issue-airworthiness-evidence` in any phase's `:auto` set)
-- see `aerospace.phase`'s docstring and `test/aerospace/
phase_test.clj`'s `dispatch-assembly-never-auto-at-any-phase`/
`issue-airworthiness-evidence-never-auto-at-any-phase`. The actor may
draft, check and recommend; a human manufacturing engineer is always
the one who actually dispatches an assembly action or issues
airworthiness evidence. Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/
`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/
`9412`/`8720`/`8521`/`6619`/`3600`/`6190`, this actor has TWO actuation
events, both POSITIVE (issuing/finalizing a real record), matching the
majority pattern in this fleet (`3600`/`6190` are the fleet's two
NEGATIVE-actuation exceptions).

## The core contract

```
assembly intake + jurisdiction facts (aerospace.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Aerospace    │ ─────────────▶ │ Aerospace                     │  (independent system)
   │ Advisor      │  + citations    │ Manufacturing Governor:      │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ assembly-tolerance-
                           record + ledger  escalate ─▶ human   out-of-range (two-
                                             (ALWAYS for         sided range) ·
                                              :actuation/dispatch-      NDT-defect-
                                              assembly /               unresolved (unconditional) ·
                                              :actuation/issue-         already-dispatched/-certified
                                              airworthiness-evidence)
```

**The Aerospace Advisor never dispatches an assembly action or issues
airworthiness evidence the Aerospace Manufacturing Governor would
reject, and never does so without a human sign-off.** Hard violations
(fabricated certification requirements; unsupported evidence; a
dimensional tolerance out of its own spec bounds; an unresolved NDT
defect; a double dispatch or evidence issuance) force **hold** and
*cannot* be approved past; a clean dispatch/evidence proposal still
always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of `kotoba.robotics.ui`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an assembly and inspection
robot performs fastening, layup and NDT on airframe structures under
the actor, gated by the independent **Aerospace Manufacturing
Governor**. The governor never dispatches hardware itself; `:high`/
`:safety-critical` actions (such as handling flight-critical
structures, composites and large assemblies) require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Aerospace Manufacturing Governor, assembly-dispatch + airworthiness-evidence draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`3030`). This vertical's assembly records are practice-specific rather
than a shared cross-manufacturer data contract, so `aerospace.*` runs
on the generic robotics/CAE/CFD/dmn/bpmn/audit-ledger/optimization
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/aerospace/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate assembly-dispatch/airworthiness-evidence history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded assembly, and the double-actuation guards check dedicated `:assembly-dispatched?`/`:airworthiness-certified?` booleans rather than a `:status` value |
| `src/aerospace/registry.cljc` | Assembly-dispatch + airworthiness-evidence draft records, plus `assembly-tolerance-out-of-range?` -- the FOURTH instance of this fleet's two-sided range check family (`testlab`/`conservation`/`water` established the first three) |
| `src/aerospace/facts.cljc` | Per-jurisdiction airworthiness-certification catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/aerospace/aerospaceadvisor.cljc` | **Aerospace Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/verification/NDT-defect-screening/assembly-dispatch/airworthiness-evidence proposals |
| `src/aerospace/governor.cljc` | **Aerospace Manufacturing Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · assembly-tolerance-out-of-range, pure ground-truth two-sided-range recompute · NDT-defect-unresolved, unconditional evaluation, the TWENTY-SEVENTH grounding of this discipline and FIRST specifically for an NDT-defect concept) + already-dispatched/already-certified guards + 1 soft (confidence/actuation gate) |
| `src/aerospace/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both assembly dispatch and airworthiness-evidence issuance always human; assembly intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/aerospace/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/aerospace/sim.cljc` | demo driver |
| `test/aerospace/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers assembly intake through requirements verification,
NDT-defect screening, assembly-action dispatch and airworthiness-
evidence issuance -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Assembly intake + per-jurisdiction airworthiness-certification checklisting, HARD-gated on an official spec-basis citation (`:assembly/intake`/`:requirements/verify`) | Real fab/assembly-line control-system integration, real robot motion-planning/force-control (see `aerospace.facts`'s docstring) |
| NDT-defect screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:ndt/screen`) | Real finite-element CAE/CFD simulation engine |
| Assembly-action dispatch, HARD-gated on full evidence and dimensional-tolerance sufficiency, plus a double-dispatch guard (`:actuation/dispatch-assembly`) | Type-certification and production-certificate application processes themselves |
| Airworthiness-evidence issuance, HARD-gated on full evidence and a double-issuance guard (`:actuation/issue-airworthiness-evidence`) | |
| Immutable audit ledger for every intake/verification/screening/dispatch/issuance decision | |

Extending coverage is additive: add the next gate (e.g. a final-
assembly-torque-audit check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`aerospace.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `aerospace.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `aerospace.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `Aerospace Advisor` + `Aerospace Manufacturing
Governor` run as real, tested code (see `Run` above), promoted from
the originally-published `:blueprint`-tier scaffold, modeled closely
on the forty-two prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
