(ns aerospace.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean assembly through
  intake -> requirements verification -> NDT-defect screening ->
  assembly-dispatch proposal (always escalates) -> human approval ->
  commit, then through airworthiness-evidence proposal (always
  escalates) -> human approval -> commit, then shows five HARD holds
  (a jurisdiction with no spec-basis, an out-of-spec assembly
  tolerance, an unresolved NDT defect screened directly via `:ndt/
  screen` [never via an actuation op against an unscreened assembly --
  see this actor's own governor ns docstring / the lesson
  `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s and `telecom`'s
  ADR-0001s already recorded], and a double assembly-dispatch/
  airworthiness-evidence-issuance of an already-processed assembly)
  that never reach a human at all, and prints the audit ledger + the
  draft assembly-dispatch and airworthiness-evidence records."
  (:require [langgraph.graph :as g]
            [aerospace.store :as store]
            [aerospace.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :manufacturing-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== assembly/intake assembly-1 (JPN, clean; tolerance within spec, no NDT defect) ==")
    (println (exec! actor "t1" {:op :assembly/intake :subject "assembly-1"
                                :patch {:id "assembly-1" :unit-name "Sakura Wing-Spar Section 4"}} operator))

    (println "== requirements/verify assembly-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :requirements/verify :subject "assembly-1"} operator))
    (println (approve! actor "t2"))

    (println "== ndt/screen assembly-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :ndt/screen :subject "assembly-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/dispatch-assembly assembly-1 (always escalates -- actuation/dispatch-assembly) ==")
    (let [r (exec! actor "t4" {:op :actuation/dispatch-assembly :subject "assembly-1"} operator)]
      (println r)
      (println "-- human manufacturing engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-airworthiness-evidence assembly-1 (always escalates -- actuation/issue-airworthiness-evidence) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-airworthiness-evidence :subject "assembly-1"} operator)]
      (println r)
      (println "-- human manufacturing engineer approves --")
      (println (approve! actor "t5")))

    (println "== requirements/verify assembly-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :requirements/verify :subject "assembly-2" :no-spec? true} operator))

    (println "== requirements/verify assembly-3 (escalates -- human approves; sets up the out-of-spec test) ==")
    (println (exec! actor "t7" {:op :requirements/verify :subject "assembly-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/dispatch-assembly assembly-3 (0.35 outside [-0.10,0.10] tolerance -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/dispatch-assembly :subject "assembly-3"} operator))

    (println "== ndt/screen assembly-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :ndt/screen :subject "assembly-4"} operator))

    (println "== actuation/dispatch-assembly assembly-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/dispatch-assembly :subject "assembly-1"} operator))

    (println "== actuation/issue-airworthiness-evidence assembly-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-airworthiness-evidence :subject "assembly-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft assembly-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft airworthiness-evidence records ==")
    (doseq [r (store/evidence-history db)] (println r))))
