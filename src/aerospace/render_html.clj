(ns aerospace.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo (com-junkawasaki/root
  ADR-2607189300): the file `docs/samples/operator-console.html`
  previously checked in here was a hand-written stub describing a
  ROBOTICS console (missions, `robot-1`, grasp actions) -- a different
  actor's domain entirely, copied in before this repo had a generator.
  Nothing on it came from this repo's code, and none of its ids
  (`M1`, `A1`, `A2`) exist in `aerospace.store/demo-data`.

  This namespace replaces it by driving the REAL actor stack --
  `aerospace.operation` (a langgraph-clj StateGraph) -> `aerospace.
  governor` (the independent Aerospace Manufacturing Governor) ->
  `aerospace.phase` (the rollout gate) -> `aerospace.store` (the SSoT +
  append-only ledger) -- and rendering the page out of the resulting
  store, ledger and per-run actor state. Every id, number, verdict,
  disposition, hold rule and draft record number on the page is read
  back out of actor output; none of them is a literal typed into this
  file. Even the action-gate table is derived (from `aerospace.phase/
  write-ops` + each phase's `:auto` set + `aerospace.governor/high-
  stakes` + `governor/confidence-floor`) rather than described by hand,
  and the jurisdiction coverage table comes from `aerospace.facts/
  coverage` over the catalog plus the jurisdictions actually present on
  the seeded assemblies -- so `ATL` shows up as genuinely uncovered
  because `assembly-2` really carries it, not because it was listed
  here.

  The scenario is adapted from this repo's own `aerospace.sim` demo
  driver (`clojure -M:dev:run`, run BEFORE writing this file to confirm
  it produces a sensible ledger against the real seeded assembly ids
  `assembly-1`..`assembly-4`), so the console shows BOTH dispositions:
  clean paths (one phase-3 auto-commit and four human-approved
  commits, including both real-world actuations) and five distinct HARD
  governor holds that never reach a human at all.

  Determinism: the whole pipeline is pure + deterministic (the mock
  Aerospace Advisor, a fresh `store/seed-db`, jurisdiction-scoped
  sequence numbers). There are no timestamps in the page content and no
  random ids, so two runs from the same seed are byte-identical -- and
  this actor's store has no clock at all, so there is no epoch to pass
  in. Set iteration is never rendered directly: every set is sorted
  before it reaches the page.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [aerospace.facts :as facts]
            [aerospace.governor :as governor]
            [aerospace.operation :as op]
            [aerospace.phase :as phase]
            [aerospace.store :as store]
            [langgraph.graph :as g]))

;; ----------------------------- driving the real actor -----------------------------

(def ^:private operator
  "The human manufacturing engineer on whose behalf the actor runs --
  the same context `aerospace.sim` uses."
  {:actor-id "op-1" :actor-role :manufacturing-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- fact-of [audit t]
  (first (filter #(= t (:t %)) audit)))

(defn- step!
  "Runs ONE operation through the real actor graph and, when the graph
  pauses at `:request-approval` (langgraph `interrupt-before`), resumes
  it with a human approval. Returns a step map whose every field is
  read back out of the actor's own final state and audit channel --
  the disposition, the escalation reason, the confidence and the hold
  rules are all the actor's, not this file's."
  [actor tid request]
  (let [r1        (exec! actor tid request)
        s1        (:state r1)
        audit1    (vec (:audit s1))
        escalate? (= :escalate (:disposition s1))
        s2        (when escalate? (:state (approve! actor tid)))
        audit2    (vec (:audit s2))
        final     (or s2 s1)
        requested (fact-of audit1 :approval-requested)
        granted   (fact-of audit2 :approval-granted)
        hold      (fact-of (into audit1 audit2) :governor-hold)]
    {:tid         tid
     :op          (:op request)
     :subject     (:subject request)
     :confidence  (:confidence (:proposal s1))
     :summary     (:summary (:proposal s1))
     :disposition (:disposition final)
     :path        (cond hold      :hard-hold
                        granted   :human-approved
                        :else     :auto-commit)
     :reason      (:reason requested)
     :approved-by (:by granted)
     :violations  (vec (:violations hold))}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every
  disposition this actor can reach, and returns `{:db .. :steps ..}`.

  Clean paths: `assembly-1` (JPN, tolerance 0.05 inside its own
  [-0.10,0.10] spec bounds, no unresolved NDT defect) walks its whole
  lifecycle -- intake (the ONLY op in phase 3's `:auto` set, so it
  auto-commits when the governor is clean), a JPN airworthiness
  requirements verification (phase-gated -- approved), an NDT-defect
  screening (phase-gated -- approved), an assembly dispatch (ALWAYS
  escalates: `:actuation/dispatch-assembly` is in NO phase's `:auto`
  set AND is in `governor/high-stakes` -- two independent layers agree
  -- approved) and an airworthiness-evidence issuance (same posture --
  approved).

  Five HARD holds, none of which ever reaches a human:
    `assembly-2` -- a requirements verification for a jurisdiction with
                    no official spec-basis in `aerospace.facts`
                    (:no-spec-basis).
    `assembly-3` -- clears its own JPN verification (approved), then a
                    dispatch attempt is blocked because the governor
                    INDEPENDENTLY recomputes its measured 0.35 against
                    its own recorded [-0.10,0.10] bounds
                    (:assembly-tolerance-out-of-range).
    `assembly-4` -- an NDT screening that itself finds an unresolved
                    defect (:ndt-defect-unresolved).
    `assembly-1` -- a SECOND dispatch of the same assembly
                    (:already-dispatched), and a SECOND
                    airworthiness-evidence issuance
                    (:already-certified).

  Returns the live store plus the ordered step maps; `render` below
  reads everything it prints out of these two."
  []
  (let [db    (store/seed-db)
        actor (op/build db)
        steps [(step! actor "t1" {:op :assembly/intake :subject "assembly-1"
                                  :patch {:id "assembly-1"
                                          :unit-name "Sakura Wing-Spar Section 4"}})
               (step! actor "t2" {:op :requirements/verify :subject "assembly-1"})
               (step! actor "t3" {:op :ndt/screen :subject "assembly-1"})
               (step! actor "t4" {:op :actuation/dispatch-assembly :subject "assembly-1"})
               (step! actor "t5" {:op :actuation/issue-airworthiness-evidence :subject "assembly-1"})
               (step! actor "t6" {:op :requirements/verify :subject "assembly-2" :no-spec? true})
               (step! actor "t7" {:op :requirements/verify :subject "assembly-3"})
               (step! actor "t8" {:op :actuation/dispatch-assembly :subject "assembly-3"})
               (step! actor "t9" {:op :ndt/screen :subject "assembly-4"})
               (step! actor "t10" {:op :actuation/dispatch-assembly :subject "assembly-1"})
               (step! actor "t11" {:op :actuation/issue-airworthiness-evidence :subject "assembly-1"})]]
    {:db db :steps steps}))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- label
  "`:cites` entries are keywords in some proposals and plain strings
  (legal-basis / provenance URLs) in others -- render both."
  [x]
  (if (keyword? x) (name x) (str x)))

(defn- join-labels [xs]
  (str/join ", " (map label xs)))

(defn- yes-no [b ok-class ok-text other-text]
  (if b
    (format "<span class=\"%s\">%s</span>" ok-class (esc ok-text))
    (format "<span class=\"muted\">%s</span>" (esc other-text))))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" (esc %) "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; --- assemblies (the SSoT) ---

(defn- assembly-row
  [{:keys [id unit-name jurisdiction
           dimensional-tolerance-actual dimensional-tolerance-min dimensional-tolerance-max
           ndt-defect-unresolved? assembly-dispatched? airworthiness-certified?
           dispatch-number evidence-number]}]
  (row (str "<code>" (esc id) "</code>")
       (esc unit-name)
       (esc jurisdiction)
       (format "<span class=\"num\">%s</span> <span class=\"muted\">in [%s, %s]</span>"
               (esc dimensional-tolerance-actual)
               (esc dimensional-tolerance-min) (esc dimensional-tolerance-max))
       (if ndt-defect-unresolved?
         "<span class=\"critical\">unresolved</span>"
         "<span class=\"ok\">none on file</span>")
       (if assembly-dispatched?
         (format "<span class=\"ok\">dispatched</span> <code>%s</code>" (esc dispatch-number))
         (yes-no false nil nil "not dispatched"))
       (if airworthiness-certified?
         (format "<span class=\"ok\">issued</span> <code>%s</code>" (esc evidence-number))
         (yes-no false nil nil "not issued"))))

;; --- operation runs (real dispositions) ---

(def ^:private path-cell
  {:auto-commit    "<span class=\"ok\">auto-commit</span>"
   :human-approved "<span class=\"warn\">human approval</span>"
   :hard-hold      "<span class=\"critical\">HARD hold</span>"})

(defn- step-row [{:keys [tid op subject confidence path reason approved-by violations summary]}]
  (row (str "<code>" (esc tid) "</code>")
       (str "<code>" (esc op) "</code>")
       (str "<code>" (esc subject) "</code>")
       (format "<span class=\"num\">%s</span>" (esc confidence))
       (get path-cell path "<span class=\"muted\">unknown</span>")
       (case path
         :hard-hold      (str "<span class=\"err\">" (esc (join-labels (map :rule violations))) "</span>")
         :human-approved (str "escalated <code>" (esc reason) "</code> &rarr; approved by "
                              "<code>" (esc approved-by) "</code> &rarr; committed")
         (str "<span class=\"muted\">" (esc summary) "</span>"))))

;; --- governor holds ---

(defn- hold-rows [steps]
  (for [{:keys [op subject violations]} steps
        v violations]
    (row (str "<code>" (esc op) "</code>")
         (str "<code>" (esc subject) "</code>")
         (str "<span class=\"critical\">" (esc (:rule v)) "</span>")
         (esc (:detail v)))))

;; --- action gate, derived from phase + governor ---

(defn- gate-row [op]
  (let [auto?    (contains? (get-in phase/phases [phase/default-phase :auto]) op)
        stakes?  (contains? governor/high-stakes op)
        enabled  (sort (for [[p {:keys [writes]}] phase/phases
                             :when (contains? writes op)]
                         p))]
    (row (str "<code>" (esc op) "</code>")
         (esc (str/join ", " enabled))
         (cond
           stakes? "<span class=\"critical\">never auto &middot; any phase</span>"
           auto?   "<span class=\"ok\">auto-commit when governor-clean</span>"
           :else   "<span class=\"warn\">human approval</span>")
         (if stakes?
           "<span class=\"warn\">phase gate AND governor high-stakes &mdash; two independent layers</span>"
           "<span class=\"muted\">phase gate</span>"))))

;; --- jurisdiction coverage ---

(defn- coverage-row [iso3 covered?]
  (let [sb (facts/spec-basis iso3)]
    (row (str "<code>" (esc iso3) "</code>")
         (if covered?
           (str "<span class=\"ok\">" (esc (:name sb)) "</span>")
           "<span class=\"critical\">no spec-basis</span>")
         (esc (or (:owner-authority sb) "—"))
         (esc (or (:legal-basis sb) "—"))
         (if sb
           (format "<span class=\"num\">%s</span>" (count (:required-evidence sb)))
           "<span class=\"muted\">0</span>"))))

;; --- draft records ---

(defn- record-row [r]
  (row (str "<code>" (esc (get r "record_id")) "</code>")
       (esc (get r "kind"))
       (str "<code>" (esc (get r "assembly_id")) "</code>")
       (esc (get r "jurisdiction"))
       (if (get r "immutable")
         "<span class=\"ok\">immutable</span>"
         "<span class=\"muted\">mutable</span>")))

;; --- ledger ---

(defn- ledger-row [{:keys [t op subject disposition basis summary violations]}]
  (row (if (= :governor-hold t)
         (str "<span class=\"critical\">" (esc (name t)) "</span>")
         (str "<span class=\"ok\">" (esc (name t)) "</span>"))
       (str "<code>" (esc op) "</code>")
       (str "<code>" (esc subject) "</code>")
       (esc (name (or disposition :n-a)))
       (esc (join-labels basis))
       (esc (or summary (str/join " / " (map :detail violations)) ""))))

(defn render
  "Renders the whole operator-console document from a `db` + `steps`
  that `run-demo!` (or any other real scenario) has already produced.
  Reads only actor output."
  [db steps]
  (let [ledger      (vec (store/ledger db))
        assemblies  (store/all-assemblies db)
        holds       (filterv #(= :governor-hold (:t %)) ledger)
        commits     (filterv #(= :committed (:t %)) ledger)
        approved    (filterv #(= :human-approved (:path %)) steps)
        autos       (filterv #(= :auto-commit (:path %)) steps)
        jurisdictions (vec (sort (into #{} (concat (keys facts/catalog)
                                                   (map :jurisdiction assemblies)))))
        cov         (facts/coverage jurisdictions)
        covered     (set (:covered-jurisdictions cov))]
    (str
     "<!doctype html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-3030 &middot; aerospace manufacturing &mdash; Operator Console</title>\n"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Manufacture of air and spacecraft (ISIC 3030) &mdash; Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample &middot; governor-gated &middot; assembly dispatch / airworthiness evidence always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "Run summary"
      (str "Generated at build time by <code>aerospace.render-html</code> "
           "(<code>clojure -M:dev:render-html</code>) from a real "
           "<code>aerospace.operation</code> StateGraph run against a fresh "
           "<code>aerospace.store/seed-db</code>. No timestamps, no random ids &mdash; "
           "reruns from the same seed are byte-identical.")
      (table ["Operations run" "Auto-committed" "Human-approved" "HARD holds" "Ledger facts" "Assemblies"]
             [(row (format "<span class=\"num\">%s</span>" (count steps))
                   (format "<span class=\"ok num\">%s</span>" (count autos))
                   (format "<span class=\"warn num\">%s</span>" (count approved))
                   (format "<span class=\"critical num\">%s</span>" (count holds))
                   (format "<span class=\"num\">%s</span>" (count ledger))
                   (format "<span class=\"num\">%s</span>" (count assemblies)))]))

     (section
      "Assemblies (SSoT)"
      (str "Live state of <code>aerospace.store</code> after the run. "
           "Dispatch and airworthiness-evidence numbers are the jurisdiction-scoped "
           "references <code>aerospace.registry</code> actually drafted; "
           "<code>:assembly-dispatched?</code> / <code>:airworthiness-certified?</code> "
           "are dedicated booleans, never a <code>:status</code> value.")
      (table ["Assembly" "Unit" "Jurisdiction" "Dimensional tolerance" "NDT defect"
              "Assembly dispatch" "Airworthiness evidence"]
             (map assembly-row assemblies)))

     (section
      "Operations this run"
      (str "One row per StateGraph run. <code>Path</code> is the disposition the actor "
           "actually reached: a phase-3 auto-commit, a human approval resumed through "
           "langgraph <code>interrupt-before #{:request-approval}</code>, or a HARD "
           "governor hold that never reaches a human at all.")
      (table ["Thread" "Op" "Assembly" "Confidence" "Path" "Outcome"]
             (map step-row steps)))

     (section
      "HARD governor holds"
      (str "Every check in <code>aerospace.governor</code> is HARD: an approver "
           "<em>cannot</em> override them. These " (count holds) " proposals were "
           "rejected before any human was asked, and nothing touched the SSoT.")
      (table ["Op" "Assembly" "Rule" "Detail"] (hold-rows steps)))

     (section
      "Action gate"
      (str "Derived from <code>aerospace.phase/write-ops</code>, each phase's "
           "<code>:auto</code> set and <code>aerospace.governor/high-stakes</code> "
           "&mdash; not hand-described. Confidence floor: <code>"
           (esc governor/confidence-floor) "</code>. Current phase: <code>"
           (esc phase/default-phase) "</code> (<code>"
           (esc (get-in phase/phases [phase/default-phase :label])) "</code>).")
      (table ["Op" "Writable in phases" "Phase 3 disposition" "Enforced by"]
             (map gate-row (sort-by str phase/write-ops))))

     (section
      "Jurisdiction spec-basis coverage"
      (str "Reported honestly by <code>aerospace.facts/coverage</code> over the catalog "
           "plus every jurisdiction actually present on a seeded assembly: "
           (:covered cov) " of " (:requested cov) " covered. A jurisdiction absent from "
           "the catalog has NO spec-basis &mdash; the advisor must not invent one, and "
           "the governor holds if it tries.")
      (table ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Required evidence"]
             (map #(coverage-row % (contains? covered %)) jurisdictions)))

     (section
      "Draft records (append-only)"
      (str "What <code>aerospace.registry</code> built for the committed actuations. "
           "These are UNSIGNED drafts &mdash; signature is the manufacturer's own act, "
           "not this actor's. This actor touches no real fab or assembly-line control system.")
      (table ["Record" "Kind" "Assembly" "Jurisdiction" "Status"]
             (map record-row (concat (store/dispatch-history db)
                                     (store/evidence-history db)))))

     (section
      "Audit ledger"
      (str "The append-only decision-fact log &mdash; " (count commits) " commits and "
           (count holds) " holds. &quot;Which assembly was screened, which action was "
           "dispatched, which airworthiness evidence was issued, on what jurisdictional "
           "basis, approved by whom&quot; is always a query over an immutable log.")
      (table ["Fact" "Op" "Assembly" "Disposition" "Basis" "Detail"]
             (map ledger-row ledger)))

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-3030 &mdash; ISIC 3030 Manufacture of air and spacecraft "
     "and related machinery. Generated from real actor output by "
     "<code>aerospace.render-html</code>; regenerate with "
     "<code>clojure -M:dev:render-html</code>.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db steps]} (run-demo!)
        ledger (vec (store/ledger db))
        holds  (filterv #(= :governor-hold (:t %)) ledger)]
    ;; Build-time invariant, not a convention: this console exists to show
    ;; that the Aerospace Manufacturing Governor can refuse. A run that
    ;; produced no HARD hold would render a page that quietly claims
    ;; everything is permitted, so refuse to write it at all.
    (when (empty? holds)
      (throw (ex-info (str "refusing to write " out
                           ": the ledger contains ZERO :governor-hold entries. "
                           "The operator console must demonstrate at least one HARD "
                           "governor hold (a hold that never reaches a human). "
                           "Fix the scenario in `run-demo!` -- do not weaken this check.")
                      {:out out
                       :ledger-facts (count ledger)
                       :governor-holds 0})))
    (spit out (render db steps) :encoding "UTF-8")
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " HARD governor holds, "
                  (count (filter #(= :human-approved (:path %)) steps)) " human-approved, "
                  (count (filter #(= :auto-commit (:path %)) steps)) " auto-committed, "
                  (count (store/dispatch-history db)) " dispatch drafts, "
                  (count (store/evidence-history db)) " airworthiness-evidence drafts)"))))
