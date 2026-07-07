# ADR-0001: Aerospace Advisor ⊣ Aerospace Manufacturing Governor architecture

## Status

Accepted. `cloud-itonami-isic-3030` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-3030` publishes an OSS business blueprint for
aircraft and aerospace manufacturing enablement: airframe assembly,
composite layup and verification, run by a certificated manufacturer
so an aerospace supplier keeps its own operating records instead of
renting a closed SaaS. Like every prior actor in this fleet, the
blueprint alone is not an implementation: this ADR records the
governed-actor architecture that promotes it to real, tested code,
following the same langgraph-clj StateGraph + independent Governor +
Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across forty-two prior siblings, most
recently `cloud-itonami-isic-6190` (telecom access).

## Decision

### Decision 1: this fleet's FIRST manufacturing vertical

Every prior actor in this fleet has been a human-services, leisure,
financial-services, or (most recently, `3600`/`6190`) infrastructure/
utility domain. `cloud-itonami-isic-3030` is the FIRST to model a
MANUFACTURING process -- airframe assembly. This is also the domain
where this fleet's standing "a robot performs the physical domain
work" premise (every cloud-itonami README's Robotics-premise section)
is most literally true: the robot is not a service-delivery aid but
the actual fabrication mechanism, and the governed-actor pattern's
"the advisor proposes, the governor gates real-world dispatch" shape
maps directly onto a real manufacturing-engineering safety concern
(never let an LLM decide when a robot fastens a flight-critical part).

### Decision 2: entity and op shape

The primary entity is an `assembly` (an airframe assembly/component
unit under manufacture, analogous to `water.store`'s `site` or
`telecom.store`'s `line`). Five ops: `:assembly/intake` (directory
upsert, no capital risk), `:requirements/verify` (per-jurisdiction
airworthiness-certification evidence checklist, never auto --
analogous to `water.operation`'s `:jurisdiction/assess`), `:ndt/
screen` (NDT-defect screening, unconditional-evaluation discipline,
never auto), `:actuation/dispatch-assembly` (POSITIVE, high-stakes --
dispatching the real robot fastening/layup/NDT action), and
`:actuation/issue-airworthiness-evidence` (POSITIVE, high-stakes --
issuing the real airworthiness-certifying evidence). This is the SAME
dual-actuation-on-one-entity shape `school`/`association`/`leasing`/
`behavioral`/`secondary`/`card`/`water`/`telecom` all use -- and,
unlike `water`/`telecom`, BOTH actuations here are POSITIVE
(issuing/finalizing a record), matching the majority shape in this
fleet's history.

### Decision 3: `assembly-tolerance-out-of-range?` -- the 4th two-sided range check

Following `testlab.registry/within-tolerance?` (1st), `conservation.
registry/body-condition-out-of-range?` (2nd) and `water.registry/
contaminant-level-out-of-range?` (3rd), `aerospace.registry/assembly-
tolerance-out-of-range?` applies the SAME lo/hi-bounds-comparison
shape to an assembly's own measured dimensional tolerance against its
own recorded spec bounds -- a natural, direct mapping onto real
aerospace manufacturing QA (dimensional-tolerance gating is exactly
this shape in practice). It gates only `:actuation/dispatch-assembly`
(the point where an out-of-spec assembly would otherwise be
physically fastened together for real), the SAME restricted-scope
placement `water.governor`'s `contaminant-level-out-of-range-
violations` uses (gating only `:report/publish`, not the earlier
`:jurisdiction/assess`) -- directly grounded in this blueprint's own
published Trust Control: "out-of-spec assembly is blocked."

### Decision 4: `ndt-defect-unresolved-violations` -- the 27th unconditional-evaluation screening grounding

Following the discipline `casualty.governor/sanctions-violations`
established and twenty-six prior siblings (most recently `telecom.
governor/billing-dispute-unresolved-violations`, the 26th) have
applied, `ndt-defect-unresolved-violations` is evaluated
UNCONDITIONALLY -- not scoped to a specific op -- so `:ndt/screen`
itself can HARD-hold on its own finding, not merely gate the
downstream actuation. This is the 27th distinct grounding of this
exact discipline, and the FIRST specifically for an NDT-defect
concept. Exercised in tests/demo via `:ndt/screen` DIRECTLY against an
already-flagged assembly, not via an actuation op against an
unscreened assembly -- the "screen the screening op directly, not the
actuation op" lesson `parksafety`'s ADR-2607071922 Decision 5
established, now applied for a SEVENTEENTH consecutive sibling
(`facility`=8th, `school`=9th, `association`=10th, `leasing`=11th,
`behavioral`=12th, `secondary`=13th, `card`=14th, `water`=15th,
`telecom`=16th, `aerospace`=17th).

### Decision 5: dedicated double-actuation-guard booleans

`:assembly-dispatched?`/`:airworthiness-certified?` are dedicated
booleans on the `assembly` record, never a single `:status` value --
the same discipline every prior sibling governor's guards establish,
informed by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`aerospace.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in `test/aerospace/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:assembly/intake` (no
capital risk). `:requirements/verify` and `:ndt/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/dispatch-assembly`/`:actuation/issue-
airworthiness-evidence` are permanently excluded from every phase's
`:auto` set -- a structural fact, not a rollout milestone, enforced by
BOTH `aerospace.phase` and `aerospace.governor`'s `high-stakes` set
independently.

### Decision 8: no bespoke domain capability lib

Unlike `credit`/`leasing`/`card`/`telecom` (which cite a related
capability contract without requiring it directly), this vertical's
assembly records are practice-specific rather than a shared cross-
manufacturer data contract, so `aerospace.*` runs on the generic
robotics/CAE/CFD/dmn/bpmn/audit-ledger/optimization stack only -- the
same posture `9412`/`8720`/`8521` and others without a bespoke
capability lib already establish.

### Decision 9: mock + LLM advisor pair

`aerospace.aerospaceadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
dispatching a robot assembly action or auto-issuing airworthiness
evidence).

### Decision 10: blueprint.edn field-sync fixes

Two stale-scaffold inconsistencies in `blueprint.edn`, discovered
during the standard "survey blueprint scaffold" step before writing
any code, were fixed as part of this promotion (the same class of fix
`card.6619`'s, `water.3600`'s and `telecom.6190`'s own ADR-0001s
document):

1. `:itonami.blueprint/id` was the stale pre-rename value
   `"cloud-itonami-3030"` (missing `isic-`), while the repo folder,
   README title and this actor's own `:business-id` already use the
   corrected `cloud-itonami-isic-3030`. Fixed to match.
2. `:itonami.blueprint/optional-technologies` was missing entirely
   despite the `kotoba-lang/industry` registry's own entry for `"3030"`
   already stating `[:eda :telemetry]`. Fixed to match the registry
   exactly. (`:required-technologies` was already correct and needed
   no fix.)

## Alternatives considered

- **A single actuation (dispatch only), treating airworthiness-
  evidence issuance as a lower-stakes administrative note.** Rejected:
  the blueprint's own Trust Controls explicitly name "airworthiness
  evidence is mandatory" as an independent invariant from assembly
  correctness itself -- collapsing it into a non-high-stakes op would
  contradict the blueprint's own stated posture that BOTH the physical
  act and the certifying record are independently gated.
- **Modeling `:actuation/dispatch-assembly` as repeatable per-
  fastener/per-operation (matching how a real assembly line performs
  many small robot actions).** Rejected in favor of treating it as a
  single "complete this assembly unit's action" act, to match the
  established dual-actuation-on-one-entity shape (a one-time act per
  entity, guarded by a dedicated boolean) every sibling in this fleet
  uses -- modeling per-fastener granularity would require an entirely
  different entity/history shape not shared by any sibling, and is
  better left to a real production MES (manufacturing execution
  system) this actor explicitly does not claim to be.
- **Gating `assembly-tolerance-out-of-range?` at both actuation ops
  (dispatch AND evidence issuance).** Rejected to match `water.
  governor/contaminant-level-out-of-range-violations`'s precedent
  exactly: the ground-truth range check gates only the op where the
  physically-out-of-spec act would occur (`:actuation/dispatch-
  assembly`); airworthiness-evidence issuance is instead gated by the
  NDT-defect-unresolved check (a defect could be found IN an
  already-dispatched, in-tolerance assembly), keeping each HARD check
  scoped to the concern it actually detects.

## Consequences

- Forty-third actor in this fleet (42 implemented before this build),
  and the FIRST manufacturing vertical.
- Confirms the two-sided range check family generalizes to a fourth,
  genuinely distinct domain (dimensional-tolerance QA), following
  `testlab`/`conservation`/`water`.
- Establishes the 27th unconditional-evaluation screening grounding,
  the first for an NDT-defect concept.
- Two pre-existing `blueprint.edn` inconsistencies (stale ID, missing
  `:optional-technologies`) fixed as in-scope minor consistency work,
  consistent with how `card.6619`/`water.3600`/`telecom.6190` handled
  the same class of issue.
