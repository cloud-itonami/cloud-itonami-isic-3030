(ns aerospace.registry
  "Pure-function assembly-dispatch + airworthiness-evidence record
  construction -- an append-only aerospace-manufacturer book-of-record
  draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for an assembly-dispatch or
  airworthiness-evidence reference number -- every manufacturer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `aerospace.facts` uses.

  `assembly-tolerance-out-of-range?` is the FOURTH instance of this
  fleet's two-sided range check family (`testlab.registry/within-
  tolerance?` established the first, `conservation.registry/body-
  condition-out-of-range?` the second, `water.registry/contaminant-
  level-out-of-range?` the third), applying the SAME lo/hi bounds-
  comparison shape to an assembly's own measured dimensional
  tolerance against the assembly's own recorded spec bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real fab/assembly-line control system. It builds the
  RECORD a manufacturer would keep, not the act of dispatching the
  robot assembly action or issuing the airworthiness evidence itself
  (that is `aerospace.operation`'s `:actuation/dispatch-assembly`/
  `:actuation/issue-airworthiness-evidence`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn assembly-tolerance-out-of-range?
  "Does `assembly`'s own `:dimensional-tolerance-actual` fall outside
  its own `[:dimensional-tolerance-min :dimensional-tolerance-max]`
  recorded spec-bounds? A pure ground-truth check against the
  assembly's own permanent fields -- no upstream comparison needed.
  The FOURTH instance of this fleet's two-sided range check family
  (see ns docstring)."
  [{:keys [dimensional-tolerance-actual dimensional-tolerance-min dimensional-tolerance-max]}]
  (and (number? dimensional-tolerance-actual) (number? dimensional-tolerance-min) (number? dimensional-tolerance-max)
       (or (< dimensional-tolerance-actual dimensional-tolerance-min)
           (> dimensional-tolerance-actual dimensional-tolerance-max))))

(defn register-assembly-dispatch
  "Validate + construct the ASSEMBLY-DISPATCH registration DRAFT --
  the manufacturer's own act of dispatching a real robot fastening/
  layup/NDT action to complete an airframe assembly. Pure function --
  does not touch any real fab/assembly-line control system; it builds
  the RECORD a manufacturer would keep. `aerospace.governor`
  independently re-verifies the assembly's own dimensional-tolerance
  sufficiency against its own spec bounds, and blocks a double-
  dispatch for the same assembly, before this is ever allowed to
  commit."
  [assembly-id jurisdiction sequence]
  (when-not (and assembly-id (not= assembly-id ""))
    (throw (ex-info "assembly-dispatch: assembly_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "assembly-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "assembly-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DSP-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "assembly-dispatch-draft"
                "assembly_id" assembly-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "AssemblyDispatch" dispatch-number dispatch-number)}))

(defn register-airworthiness-evidence
  "Validate + construct the AIRWORTHINESS-EVIDENCE registration DRAFT
  -- the manufacturer's own act of issuing real airworthiness evidence
  certifying an assembly as flight-worthy. Pure function -- does not
  touch any real fab/assembly-line control system; it builds the
  RECORD a manufacturer would keep. `aerospace.governor` independently
  re-verifies the assembly's own NDT-defect resolution status, and
  blocks a double-issuance for the same assembly, before this is ever
  allowed to commit."
  [assembly-id jurisdiction sequence]
  (when-not (and assembly-id (not= assembly-id ""))
    (throw (ex-info "airworthiness-evidence: assembly_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "airworthiness-evidence: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "airworthiness-evidence: sequence must be >= 0" {})))
  (let [evidence-number (str (str/upper-case jurisdiction) "-AWE-" (zero-pad sequence 6))
        record {"record_id" evidence-number
                "kind" "airworthiness-evidence-draft"
                "assembly_id" assembly-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "evidence_number" evidence-number
     "certificate" (unsigned-certificate "AirworthinessEvidence" evidence-number evidence-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
