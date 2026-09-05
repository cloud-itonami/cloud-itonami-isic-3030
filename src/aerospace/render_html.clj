(ns aerospace.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL aerospace-manufacturing OperationActor
  (`aerospace.operation/build` -> a compiled langgraph-clj StateGraph)
  over the REAL seeded store (`aerospace.store/seed-db`), through the
  REAL Aerospace Manufacturing Governor (`aerospace.governor/check`)
  and the REAL rollout phase gate (`aerospace.phase/gate`), then
  renders whatever those actually produced. Nothing on this page is a
  hand-typed observation:

    - every assembly row is read back out of the store after the run
      (`store/all-assemblies`, `store/requirements-verification-of`,
      `store/ndt-screen-of`, `store/dispatch-history`,
      `store/evidence-history`),
    - every HARD-hold rule name and every violation detail string is
      the governor's OWN `:violations` entry off the ledger fact --
      never a literal in this namespace,
    - the phase table is derived from `aerospace.phase/phases` /
      `aerospace.phase/write-ops`, the governor configuration from
      `aerospace.governor` public vars, and the jurisdiction coverage
      from `aerospace.facts/coverage` (which reports honestly, and is
      allowed to say a jurisdiction is missing).

  The ONE hand-written table on the page is the `real-world-act`
  column of the op-gate contract section -- a static description of
  what each op means in the physical world. It is labelled as static
  both in the source (see `op-real-world-act`) and on the page itself.
  Every other cell in that same table is derived from the phase/
  governor vars.

  Subject provenance (the demo may not invent subjects): every subject
  driven below is one of the four assemblies seeded by
  `aerospace.store/demo-data` -- `assembly-1` (JPN, tolerance 0.05
  within spec, NDT clean), `assembly-2` (jurisdiction `ATL`, which
  deliberately has NO entry in `aerospace.facts/catalog`),
  `assembly-3` (JPN, tolerance 0.35 OUTSIDE its own recorded
  [-0.10,0.10] spec bounds) and `assembly-4` (JPN, NDT defect
  unresolved). No id is invented here.

  Deterministic: no clock, no randomness, no network, no timestamp in
  the page content. Re-running writes a byte-identical file.

  Run: `clojure -M:dev:render-html [out-file]`
  (default out-file `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [aerospace.facts :as facts]
            [aerospace.governor :as governor]
            [aerospace.operation :as op]
            [aerospace.phase :as phase]
            [aerospace.store :as store]))

;; ----------------------------- the run -----------------------------

(def ^:private engineer
  {:actor-id "op-1" :actor-role :manufacturing-engineer :phase phase/default-phase})

(def ^:private scenarios
  "One entry = one operation driven through the real compiled actor.
  `:approval`, when present, is the human decision handed back to the
  graph while it is paused at `:request-approval`
  (`interrupt-before #{:request-approval}`); `:context` overrides the
  operator context for that one run.

  Ordering is load-bearing: t11/t12 can only reach the double-actuation
  guards because t04/t05 committed first, and t09 can only reach the
  tolerance check because t08 put a satisfied evidence checklist on
  file (otherwise the earlier `:evidence-incomplete` check fires
  instead). Between them these fourteen runs reach all SIX of the
  governor's HARD rules exactly once each, plus one phase-gate hold
  and one human rejection."
  [;; --- assembly-1: one full clean lifecycle -------------------------------
   {:tid "t01" :request {:op :assembly/intake :subject "assembly-1"
                         :patch {:id "assembly-1" :unit-name "Sakura Wing-Spar Section 4"}}}
   {:tid "t02" :request {:op :requirements/verify :subject "assembly-1"}
    :approval {:status :approved :by "op-1"}}
   {:tid "t03" :request {:op :ndt/screen :subject "assembly-1"}
    :approval {:status :approved :by "op-1"}}
   {:tid "t04" :request {:op :actuation/dispatch-assembly :subject "assembly-1"}
    :approval {:status :approved :by "op-1"}}
   {:tid "t05" :request {:op :actuation/issue-airworthiness-evidence :subject "assembly-1"}
    :approval {:status :approved :by "op-1"}}

   ;; --- the six HARD governor rules, one run each ---------------------------
   {:tid "t06" :request {:op :requirements/verify :subject "assembly-2" :no-spec? true}}
   {:tid "t07" :request {:op :actuation/dispatch-assembly :subject "assembly-2"}}
   {:tid "t08" :request {:op :requirements/verify :subject "assembly-3"}
    :approval {:status :approved :by "op-1"}}
   {:tid "t09" :request {:op :actuation/dispatch-assembly :subject "assembly-3"}}
   {:tid "t10" :request {:op :ndt/screen :subject "assembly-4"}}
   {:tid "t11" :request {:op :actuation/dispatch-assembly :subject "assembly-1"}}
   {:tid "t12" :request {:op :actuation/issue-airworthiness-evidence :subject "assembly-1"}}

   ;; --- the two non-governor holds -----------------------------------------
   ;; a governor-clean proposal a person declined
   {:tid "t13" :request {:op :requirements/verify :subject "assembly-4"}
    :approval {:status :rejected :by "op-1"}}
   ;; the SECOND independent layer: same governor-clean proposal, but the
   ;; operator is running at phase 1, where :requirements/verify may not write
   {:tid "t14" :request {:op :requirements/verify :subject "assembly-1"}
    :context (assoc engineer :phase 1)}])

(defn- drive!
  "Runs one scenario through the real compiled graph and returns the
  scenario enriched with what the graph actually did."
  [actor {:keys [tid request approval context] :as scenario}]
  (let [r1 (g/run* actor {:request request :context (or context engineer)}
                   {:thread-id tid})
        paused? (= :interrupted (:status r1))
        r2 (when (and approval paused?)
             (g/run* actor {:approval approval} {:thread-id tid :resume? true}))
        final (:state (or r2 r1))
        audit (:audit final [])]
    (assoc scenario
           :phase (:phase (or context engineer))
           :verdict (:verdict final)
           :paused? paused?
           :escalation (first (filter #(= :approval-requested (:t %)) audit))
           :human (when r2 (:status approval))
           :disposition (:disposition final))))

(defn run-demo!
  "Seeds a MemStore with `aerospace.store/demo-data`, builds the real
  actor and drives every scenario above through it. Returns
  {:db store :runs [..]}."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    {:db db :runs (mapv #(drive! actor %) scenarios)}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- fmt
  "Render a stored value, or an em dash when the domain model carries
  no value for that field on that record."
  [v]
  (if (nil? v) "—" (esc v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- codes
  "Render a SEQUENCE in the order the code produced it -- used for
  `:basis`, whose order is the governor's own evaluation order."
  [coll]
  (if (seq coll) (str/join " " (map code coll)) "<span class=\"muted\">—</span>"))

(defn- kw-codes
  "Render a SET. Sorted, because a set has no order and an unsorted
  render would make the output non-deterministic."
  [coll]
  (str/join " " (map code (sort-by str coll))))

(defn- flag [v]
  (if (true? v)
    "<span class=\"ok\">true</span>"
    (str "<span class=\"muted\">" (if (nil? v) "—" (esc v)) "</span>")))

(defn- tr [& cells] (str "<tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "<table><thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead><tbody>\n"
       (str/join "\n" rows)
       "\n</tbody></table>"))

(defn- card [title note body]
  (str "<section class=\"card\"><h2>" (esc title) "</h2>"
       (when note (str "<p class=\"muted\">" note "</p>"))
       body "</section>"))

;; ----------------------------- ledger views -----------------------------

(defn- ledger-of [db] (vec (store/ledger db)))

(defn- holds
  "Every `:governor-hold` fact on the ledger, HARD (a real governor
  rule violation) or phase-gated."
  [db]
  (filterv #(= :governor-hold (:t %)) (ledger-of db)))

(defn- hard-holds
  "The subset whose hold carries at least one governor `:violations`
  entry -- i.e. a real, un-overridable compliance rule fired."
  [db]
  (filterv #(seq (:violations %)) (holds db)))

(defn- phase-holds
  "The subset held purely by the rollout phase gate: the governor was
  clean (no `:violations`) but the phase forbade the write."
  [db]
  (filterv #(empty? (:violations %)) (holds db)))

;; ----------------------------- sections -----------------------------

(defn- summary-section [db runs]
  (let [led (ledger-of db)
        n (fn [t] (count (filter #(= t (:t %)) led)))]
    (card "Run summary"
          (str "Every number below is a count over the actor's own append-only ledger after "
               "driving " (count runs) " operations through " (code "aerospace.operation/build") ".")
          (table ["Measure" "Count"]
                 [(tr "operations driven" (str "<span class=\"num\">" (count runs) "</span>"))
                  (tr "ledger facts" (str "<span class=\"num\">" (count led) "</span>"))
                  (tr "commits" (str "<span class=\"num ok\">" (n :committed) "</span>"))
                  (tr "governor HARD holds (a compliance rule fired)"
                      (str "<span class=\"num critical\">" (count (hard-holds db)) "</span>"))
                  (tr "phase-gate holds (governor clean, phase forbade the write)"
                      (str "<span class=\"num warn\">" (count (phase-holds db)) "</span>"))
                  (tr "human rejections" (str "<span class=\"num critical\">"
                                              (n :approval-rejected) "</span>"))
                  (tr "assembly dispatches committed"
                      (str "<span class=\"num\">" (count (store/dispatch-history db)) "</span>"))
                  (tr "airworthiness evidences committed"
                      (str "<span class=\"num\">" (count (store/evidence-history db)) "</span>"))])
          )))

(defn- verdict-cell [{:keys [verdict]}]
  (cond
    (nil? verdict) "<span class=\"muted\">—</span>"
    (:hard? verdict)
    (str "<span class=\"critical\">HARD</span> "
         (str/join " " (map code (map :rule (:violations verdict)))))
    (:escalate? verdict)
    (str "<span class=\"warn\">escalate</span>"
         (when (:high-stakes? verdict) " <span class=\"muted\">high-stakes actuation</span>"))
    :else (str "<span class=\"ok\">clean</span> <span class=\"muted\">conf "
               (esc (:confidence verdict)) "</span>")))

(defn- human-cell [{:keys [approval human paused?]}]
  (cond
    (= :approved human) "<span class=\"ok\">approved</span>"
    (= :rejected human) "<span class=\"critical\">rejected</span>"
    (and approval (not paused?)) "<span class=\"muted\">never offered (no interrupt)</span>"
    :else "<span class=\"muted\">—</span>"))

(defn- disposition-cell [{:keys [disposition]}]
  (case disposition
    :commit "<span class=\"ok\">commit</span>"
    :hold "<span class=\"critical\">hold</span>"
    :escalate "<span class=\"warn\">escalate</span>"
    (str "<span class=\"muted\">" (fmt disposition) "</span>")))

(defn- timeline-section [runs]
  (card "Operation timeline"
        (str "One row = one " (code "langgraph.graph/run*") " over the compiled actor. The "
             "governor column is the verdict map the governor itself returned; the human column "
             "is the decision handed back to the graph while it was paused at "
             (code ":request-approval") ".")
        (table ["Thread" "Op" "Subject" "Phase" "Governor verdict" "Human" "Final"]
               (for [{:keys [tid request escalation] :as r} runs]
                 (tr (code tid)
                     (code (:op request))
                     (code (:subject request))
                     (esc (:phase r))
                     (verdict-cell r)
                     (human-cell r)
                     (str (disposition-cell r)
                          (when-let [reason (:reason escalation)]
                            (str " <span class=\"muted\">after escalation "
                                 (code reason) "</span>"))))))))

(defn- holds-section [db]
  (let [hs (hard-holds db)]
    (card "Governor HARD holds — un-overridable"
          (str "Each row is a " (code ":governor-hold") " fact on the append-only ledger. The rule "
               "name and the detail text are the governor's own " (code ":violations") " entries — "
               "this page carries no rule text of its own. A human approver CANNOT override any of "
               "these: none of them ever reached a person at all.")
          (table ["Rule" "Op" "Subject" "Advisor confidence" "Governor's own detail"]
                 (for [h hs
                       v (:violations h)]
                   (tr (str "<span class=\"critical\">" (esc (:rule v)) "</span>")
                       (code (:op h))
                       (code (:subject h))
                       (fmt (:confidence h))
                       (esc (:detail v))))))))

(defn- phase-holds-section [db]
  (let [ps (phase-holds db)]
    (when (seq ps)
      (card "Phase-gate holds — the second independent layer"
            (str "The governor cleared these proposals (" (code ":violations")
                 " is empty), and the rollout phase gate held them anyway. Two layers, not one.")
            (table ["Op" "Subject" "Phase" "Phase reason" "Advisor confidence"]
                   (for [p ps]
                     (tr (code (:op p)) (code (:subject p)) (fmt (:phase p))
                         (code (:phase-reason p)) (fmt (:confidence p)))))))))

(defn- rejections-section [db]
  (let [rs (filterv #(= :approval-rejected (:t %)) (ledger-of db))]
    (when (seq rs)
      (card "Human rejections"
            (str "A governor-clean proposal a person declined. Written to the ledger by the same "
                 (code ":hold") " node, but with basis " (code ":approver-rejected")
                 " — this is a human's call, not a compliance violation.")
            (table ["Op" "Subject" "Basis" "Advisor confidence"]
                   (for [r rs]
                     (tr (code (:op r)) (code (:subject r))
                         (codes (:basis r)) (fmt (:confidence r)))))))))

(defn- last-fact-for [led subject]
  (last (filter #(= subject (:subject %)) led)))

(defn- subject-status [led subject]
  (let [f (last-fact-for led subject)]
    (cond
      (nil? f) "<span class=\"muted\">no ledger activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">rejected by approver</span>"
      (= :governor-hold (:t f))
      (if (seq (:basis f))
        (str "<span class=\"critical\">HARD hold</span> " (codes (:basis f)))
        (str "<span class=\"warn\">phase hold</span> " (code (:phase-reason f))))
      :else (str "<span class=\"muted\">" (esc (:t f)) "</span>"))))

(defn- assemblies-section [db]
  (let [led (ledger-of db)]
    (card "Airframe assemblies"
          (str "Read back from " (code "aerospace.store/all-assemblies") " after the run. "
               "The tolerance column shows each assembly's own measured value against its own "
               "recorded spec bounds — the governor re-derives that comparison itself via "
               (code "aerospace.registry/assembly-tolerance-out-of-range?")
               " rather than believing the advisor's rationale. "
               (code ":dispatch-number") " / " (code ":evidence-number")
               " exist only where a commit actually minted one.")
          (table ["Assembly" "Unit" "Jurisdiction" "Tolerance (actual)" "Spec bounds"
                  "NDT defect unresolved?" "dispatched?" "certified?"
                  "Dispatch number" "Evidence number" "Ledger status"]
                 (for [a (store/all-assemblies db)]
                   (tr (code (:id a)) (fmt (:unit-name a)) (code (:jurisdiction a))
                       (str "<span class=\"num\">" (fmt (:dimensional-tolerance-actual a)) "</span>")
                       (str "<span class=\"num\">[" (fmt (:dimensional-tolerance-min a)) ", "
                            (fmt (:dimensional-tolerance-max a)) "]</span>")
                       (if (true? (:ndt-defect-unresolved? a))
                         "<span class=\"critical\">true</span>"
                         (flag (:ndt-defect-unresolved? a)))
                       (flag (:assembly-dispatched? a))
                       (flag (:airworthiness-certified? a))
                       (fmt (:dispatch-number a))
                       (fmt (:evidence-number a))
                       (subject-status led (:id a))))))))

(defn- verifications-section [db]
  (let [rows (keep (fn [a]
                     (when-let [v (store/requirements-verification-of db (:id a))]
                       [a v]))
                   (store/all-assemblies db))]
    (card "Requirements verifications on file"
          (str "Committed evidence checklists, read back per assembly from "
               (code "aerospace.store/requirements-verification-of") ". A checklist is only on "
               "file where the whole intake → advise → govern → approve → commit path completed; "
               "a HARD-held or human-rejected verification stores nothing at all, which is why "
               "an assembly can be present above and absent here.")
          (if (seq rows)
            (table ["Assembly" "Jurisdiction" "Legal basis" "Spec basis (official source)"
                    "Required evidence items" "Approved by"]
                   (for [[a v] rows]
                     (tr (code (:id a)) (code (:jurisdiction v)) (fmt (:legal-basis v))
                         (fmt (:spec-basis v))
                         (str "<span class=\"num\">" (count (:checklist v)) "</span>")
                         (fmt (:approved-by v)))))
            "<p class=\"muted\">none committed in this run</p>"))))

(defn- ndt-section [db]
  (let [rows (keep (fn [a]
                     (when-let [s (store/ndt-screen-of db (:id a))]
                       [a s]))
                   (store/all-assemblies db))]
    (card "NDT-defect screenings on file"
          (str "Committed screening verdicts from " (code "aerospace.store/ndt-screen-of")
               ". A screening that itself finds an unresolved defect is HARD-held, so its verdict "
               "never becomes a stored record — the finding lives on the ledger as a hold instead.")
          (if (seq rows)
            (table ["Assembly" "Verdict" "Approved by"]
                   (for [[a s] rows]
                     (tr (code (:id a))
                         (if (= :unresolved (:verdict s))
                           (str "<span class=\"critical\">" (esc (:verdict s)) "</span>")
                           (str "<span class=\"ok\">" (esc (:verdict s)) "</span>"))
                         (fmt (:approved-by s)))))
            "<p class=\"muted\">none committed in this run</p>"))))

(defn- drafts-section [db]
  (let [ds (store/dispatch-history db)
        es (store/evidence-history db)]
    (card "Book-of-record drafts (unsigned)"
          (str "The append-only draft records minted at commit time by "
               (code "aerospace.registry") " and read back from "
               (code "aerospace.store/dispatch-history") " / "
               (code "aerospace.store/evidence-history") ". These are the records a manufacturer "
               "would keep — this actor signs nothing and calls no real fab or assembly-line "
               "control system.")
          (if (or (seq ds) (seq es))
            (table ["Record id" "Kind" "Assembly" "Jurisdiction" "immutable"]
                   (for [r (concat ds es)]
                     (tr (code (get r "record_id")) (code (get r "kind"))
                         (code (get r "assembly_id")) (code (get r "jurisdiction"))
                         (flag (get r "immutable")))))
            "<p class=\"muted\">none committed in this run</p>"))))

;; ----------------------------------------------------------------------
;; STATIC CONTENT — the ONLY hand-written content on this page.
;; `op-real-world-act` describes what each op means in the physical
;; world. It is a fixed property of this actor's closed op contract
;; (README `Ops` / `Actuation`), not runtime telemetry, so it is
;; legitimately hand-described rather than derived from a live run.
;; Every OTHER column of the op-gate table below is derived from
;; `aerospace.phase/phases` and `aerospace.governor/high-stakes`.
;; ----------------------------------------------------------------------
(def ^:private op-real-world-act
  {:assembly/intake
   "Normalise an assembly record. No capital risk, no physical act."
   :requirements/verify
   "Draft the jurisdiction's airworthiness evidence checklist. Paper only."
   :ndt/screen
   "Screen the assembly for an unresolved non-destructive-testing defect. Paper only."
   :actuation/dispatch-assembly
   "Dispatch a REAL robot fastening / layup / NDT action on a flight-critical structure."
   :actuation/issue-airworthiness-evidence
   "Issue REAL airworthiness evidence certifying an assembly as flight-worthy."})

(defn- op-gate-section []
  (let [ph phase/default-phase
        {:keys [label writes auto]} (get phase/phases ph)]
    (card (str "Op-gate contract — phase " ph " (" label ")")
          (str "The <em>real-world act</em> column is the only hand-written content on this page: "
               "a static description of this actor's fixed op contract. The other three columns "
               "are derived from " (code "aerospace.phase/phases") " and "
               (code "aerospace.governor/high-stakes") ". Note that the two actuation ops are "
               "absent from EVERY phase's " (code ":auto") " set, phase 3 included — that is a "
               "permanent structural fact, not a rollout milestone still to come.")
          (table ["Op" "Real-world act (static)" "May write in this phase"
                  "May auto-commit when governor-clean" "Always a human call"]
                 (for [o (sort-by str phase/write-ops)]
                   (tr (code o)
                       (esc (get op-real-world-act o "—"))
                       (if (contains? writes o)
                         "<span class=\"ok\">yes</span>"
                         "<span class=\"critical\">no — HOLD (:phase-disabled)</span>")
                       (if (contains? auto o)
                         "<span class=\"ok\">yes</span>"
                         "<span class=\"warn\">no — always human approval</span>")
                       (if (contains? governor/high-stakes o)
                         "<span class=\"warn\">yes — high-stakes actuation</span>"
                         "<span class=\"muted\">—</span>")))))))

(defn- phase-section []
  (card "Rollout phases"
        (str "Derived from " (code "aerospace.phase/phases") ". A governor HOLD always stays a "
             "HOLD (compliance wins); an op that may write but is not auto-eligible escalates to "
             "a human even when the governor is clean. This console ran at phase "
             (code phase/default-phase) " except where the timeline says otherwise.")
        (table ["Phase" "Label" "May write" "May auto-commit"]
               (for [[p {:keys [label writes auto]}] (sort-by key phase/phases)]
                 (tr (str "<span class=\"num\">" (esc p) "</span>")
                     (esc label)
                     (if (seq writes) (kw-codes writes) "<span class=\"muted\">none</span>")
                     (if (seq auto) (kw-codes auto) "<span class=\"muted\">none</span>"))))))

(defn- governor-section [db]
  (card "Governor configuration"
        (str "Read straight off the public vars of " (code "aerospace.governor")
             ", plus the set of HARD rules this run actually made fire.")
        (table ["Setting" "Value"]
               [(tr "confidence floor" (code governor/confidence-floor))
                (tr "always-human stakes" (kw-codes governor/high-stakes))
                (tr "HARD rules that fired on this run"
                    (kw-codes (into #{} (mapcat :basis) (hard-holds db))))])))

(defn- coverage-section []
  (let [{:keys [requested covered covered-jurisdictions missing-jurisdictions note]}
        (facts/coverage)]
    (card "Jurisdiction spec-basis coverage"
          (str "Reported by " (code "aerospace.facts/coverage") ", which is required to report "
               "honestly: a jurisdiction with no entry has NO spec-basis, full stop, and the "
               "governor holds any proposal that tries to invent one.")
          (str (table ["Measure" "Value"]
                      [(tr "jurisdictions requested"
                           (str "<span class=\"num\">" (esc requested) "</span>"))
                       (tr "jurisdictions with an official spec-basis"
                           (str "<span class=\"num ok\">" (esc covered) "</span>"))
                       (tr "covered" (codes covered-jurisdictions))
                       (tr "missing" (codes missing-jurisdictions))])
               "<p class=\"muted\">" (esc note) "</p>"))))

(defn- ledger-section [db]
  (card "Audit ledger (append-only)"
        (str "The full ledger, in append order, exactly as " (code "aerospace.store/ledger")
             " returns it. Note that " (code ":approval-granted") " is emitted to the graph's "
             "in-memory " (code ":audit") " channel only — " (code "aerospace.operation")
             " never appends it to the store ledger, so it is not a fact this page counts. An "
             "approved operation is visible as the " (code ":committed") " fact it produced.")
        (table ["#" "Fact" "Op" "Subject" "Actor" "Disposition" "Basis"]
               (map-indexed
                (fn [i f]
                  (tr (str "<span class=\"num\">" (esc (inc i)) "</span>")
                      (let [cls (case (:t f)
                                  :committed "ok"
                                  :governor-hold (if (seq (:violations f)) "critical" "warn")
                                  :approval-rejected "critical"
                                  "muted")]
                        (str "<span class=\"" cls "\">" (esc (:t f)) "</span>"))
                      (code (:op f)) (code (:subject f)) (fmt (:actor f))
                      (fmt (:disposition f)) (codes (:basis f))))
                (ledger-of db)))))

;; ----------------------------- page -----------------------------

(defn render
  "Renders the whole operator console from `{:db .. :runs ..}` as
  returned by `run-demo!` (or any other real run of this actor)."
  [{:keys [db runs]}]
  (str "<!doctype html>\n<html lang=\"en\">\n<head>\n"
       "<meta charset=\"utf-8\">\n"
       "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
       "<title>cloud-itonami-isic-3030 · Aircraft and Aerospace Manufacturing Enablement — Operator Console</title>\n"
       "<style>" (jp-go-dds.skin/dds+skin) "</style>\n"
       "</head>\n<body>\n"
       "<header class=\"bar\">\n"
       "<h1>Aircraft &amp; aerospace manufacturing (ISIC 3030) — Operator Console</h1>\n"
       "</header>\n"
       "<p class=\"subtitle\">"
       "<span class=\"badge\">read-only sample</span> "
       "<span class=\"badge\">governor-gated</span> "
       "<span class=\"badge\">assembly dispatch &amp; airworthiness evidence always human-approved</span>"
       "</p>\n"
       "<main>\n"
       (str/join "\n"
                 (remove nil?
                         [(summary-section db runs)
                          (timeline-section runs)
                          (holds-section db)
                          (phase-holds-section db)
                          (rejections-section db)
                          (op-gate-section)
                          (phase-section)
                          (governor-section db)
                          (coverage-section)
                          (assemblies-section db)
                          (verifications-section db)
                          (ndt-section db)
                          (drafts-section db)
                          (ledger-section db)]))
       "\n</main>\n<footer>"
       "Generated at build time by <code>aerospace.render-html</code> "
       "(<code>clojure -M:dev:render-html</code>) by driving the real "
       "<code>aerospace.operation</code> actor graph over the real "
       "<code>aerospace.store</code> seed, through the real "
       "<code>aerospace.governor</code> and <code>aerospace.phase</code> gates. "
       "Deterministic — no clock, no randomness, no network. "
       "No usage, revenue or performance metric is claimed anywhere on this page. "
       "Styling is the デジタル庁デザインシステム (DADS) compatibility skin "
       "(<code>jp-go-dds.skin/dds+skin</code>)."
       "</footer>\n</body>\n</html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        hs (hard-holds db)]
    ;; Build-time invariant: a console that shows no real HARD hold is
    ;; not evidence of a governor. Refuse to write one.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))
                       :holds (count (holds db))})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count (phase-holds db)) " phase-gate holds, "
                  (count (:runs result)) " operations)"))))
