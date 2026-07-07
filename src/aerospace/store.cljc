(ns aerospace.store
  "SSoT for the aerospace-manufacturing actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/aerospace/store_contract_test.clj), which is the whole point:
  the actor, the Aerospace Manufacturing Governor and the audit ledger
  never know which SSoT they run on.

  Like `telecom.store`'s dual number-provisioning/billing-suppression
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (dispatching an assembly action, issuing
  airworthiness evidence) acting on the SAME entity (an assembly),
  each with its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:assembly-dispatched?`/
  `:airworthiness-certified?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which assembly was
  screened for an unresolved NDT defect, which assembly action was
  dispatched, which airworthiness evidence was issued, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting an aerospace
  manufacturer needs, and the evidence a manufacturer needs if a
  dispatch or airworthiness-evidence decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [aerospace.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (assembly [s id])
  (all-assemblies [s])
  (ndt-screen-of [s assembly-id] "committed NDT-defect screening verdict for an assembly, or nil")
  (requirements-verification-of [s assembly-id] "committed requirements verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only assembly-dispatch history (aerospace.registry drafts)")
  (evidence-history [s] "the append-only airworthiness-evidence history (aerospace.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-evidence-sequence [s jurisdiction] "next evidence-number sequence for a jurisdiction")
  (assembly-already-dispatched? [s assembly-id] "has this assembly's action already been dispatched?")
  (assembly-already-certified? [s assembly-id] "has this assembly's airworthiness evidence already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-assemblies [s assemblies] "replace/seed the assembly directory (map id->assembly)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained assembly set covering both actuation
  lifecycles (dispatching an assembly action, issuing airworthiness
  evidence) so the actor + tests run offline."
  []
  {:assemblies
   {"assembly-1" {:id "assembly-1" :unit-name "Sakura Wing-Spar Section 4"
                  :dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? false
                  :assembly-dispatched? false :airworthiness-certified? false
                  :jurisdiction "JPN" :status :intake}
    "assembly-2" {:id "assembly-2" :unit-name "Atlantis Fuselage Panel"
                  :dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? false
                  :assembly-dispatched? false :airworthiness-certified? false
                  :jurisdiction "ATL" :status :intake}
    "assembly-3" {:id "assembly-3" :unit-name "鈴木胴体パネル"
                  :dimensional-tolerance-actual 0.35 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? false
                  :assembly-dispatched? false :airworthiness-certified? false
                  :jurisdiction "JPN" :status :intake}
    "assembly-4" {:id "assembly-4" :unit-name "田中主翼桁"
                  :dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10
                  :ndt-defect-unresolved? true
                  :assembly-dispatched? false :airworthiness-certified? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-assembly!
  "Backend-agnostic `:assembly/mark-dispatched` -- looks up the
  assembly via the protocol and drafts the assembly-dispatch record,
  and returns {:result .. :assembly-patch ..} for the caller to
  persist."
  [s assembly-id]
  (let [a (assembly s assembly-id)
        seq-n (next-dispatch-sequence s (:jurisdiction a))
        result (registry/register-assembly-dispatch assembly-id (:jurisdiction a) seq-n)]
    {:result result
     :assembly-patch {:assembly-dispatched? true
                      :dispatch-number (get result "dispatch_number")}}))

(defn- issue-airworthiness-evidence!
  "Backend-agnostic `:assembly/mark-certified` -- looks up the
  assembly via the protocol and drafts the airworthiness-evidence
  record, and returns {:result .. :assembly-patch ..} for the caller
  to persist."
  [s assembly-id]
  (let [a (assembly s assembly-id)
        seq-n (next-evidence-sequence s (:jurisdiction a))
        result (registry/register-airworthiness-evidence assembly-id (:jurisdiction a) seq-n)]
    {:result result
     :assembly-patch {:airworthiness-certified? true
                      :evidence-number (get result "evidence_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (assembly [_ id] (get-in @a [:assemblies id]))
  (all-assemblies [_] (sort-by :id (vals (:assemblies @a))))
  (ndt-screen-of [_ id] (get-in @a [:ndt-screens id]))
  (requirements-verification-of [_ assembly-id] (get-in @a [:verifications assembly-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (evidence-history [_] (:evidences @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-evidence-sequence [_ jurisdiction] (get-in @a [:evidence-sequences jurisdiction] 0))
  (assembly-already-dispatched? [_ assembly-id] (boolean (get-in @a [:assemblies assembly-id :assembly-dispatched?])))
  (assembly-already-certified? [_ assembly-id] (boolean (get-in @a [:assemblies assembly-id :airworthiness-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :assembly/upsert
      (swap! a update-in [:assemblies (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :ndt-screen/set
      (swap! a assoc-in [:ndt-screens (first path)] payload)

      :assembly/mark-dispatched
      (let [assembly-id (first path)
            {:keys [result assembly-patch]} (dispatch-assembly! s assembly-id)
            jurisdiction (:jurisdiction (assembly s assembly-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:assemblies assembly-id] merge assembly-patch)
                       (update :dispatches registry/append result))))
        result)

      :assembly/mark-certified
      (let [assembly-id (first path)
            {:keys [result assembly-patch]} (issue-airworthiness-evidence! s assembly-id)
            jurisdiction (:jurisdiction (assembly s assembly-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:evidence-sequences jurisdiction] (fnil inc 0))
                       (update-in [:assemblies assembly-id] merge assembly-patch)
                       (update :evidences registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-assemblies [s assemblies] (when (seq assemblies) (swap! a assoc :assemblies assemblies)) s))

(defn seed-db
  "A MemStore seeded with the demo assembly set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :ndt-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :evidence-sequences {} :evidences []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/NDT-screen payloads, ledger facts,
  dispatch/evidence records) are stored as EDN strings so `langchain.
  db` doesn't expand them into sub-entities -- the same convention
  every sibling actor's store uses."
  {:assembly/id                       {:db/unique :db.unique/identity}
   :verification/assembly-id          {:db/unique :db.unique/identity}
   :ndt-screen/assembly-id            {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :dispatch/seq                      {:db/unique :db.unique/identity}
   :evidence/seq                      {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :evidence-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- assembly->tx [{:keys [id unit-name dimensional-tolerance-actual dimensional-tolerance-min dimensional-tolerance-max
                             ndt-defect-unresolved?
                             assembly-dispatched? airworthiness-certified?
                             jurisdiction status dispatch-number evidence-number]}]
  (cond-> {:assembly/id id}
    unit-name                                  (assoc :assembly/unit-name unit-name)
    dimensional-tolerance-actual                (assoc :assembly/dimensional-tolerance-actual dimensional-tolerance-actual)
    dimensional-tolerance-min                   (assoc :assembly/dimensional-tolerance-min dimensional-tolerance-min)
    dimensional-tolerance-max                   (assoc :assembly/dimensional-tolerance-max dimensional-tolerance-max)
    (some? ndt-defect-unresolved?)              (assoc :assembly/ndt-defect-unresolved? ndt-defect-unresolved?)
    (some? assembly-dispatched?)                (assoc :assembly/assembly-dispatched? assembly-dispatched?)
    (some? airworthiness-certified?)            (assoc :assembly/airworthiness-certified? airworthiness-certified?)
    jurisdiction                                (assoc :assembly/jurisdiction jurisdiction)
    status                                      (assoc :assembly/status status)
    dispatch-number                             (assoc :assembly/dispatch-number dispatch-number)
    evidence-number                             (assoc :assembly/evidence-number evidence-number)))

(def ^:private assembly-pull
  [:assembly/id :assembly/unit-name :assembly/dimensional-tolerance-actual
   :assembly/dimensional-tolerance-min :assembly/dimensional-tolerance-max
   :assembly/ndt-defect-unresolved? :assembly/assembly-dispatched? :assembly/airworthiness-certified?
   :assembly/jurisdiction :assembly/status :assembly/dispatch-number :assembly/evidence-number])

(defn- pull->assembly [m]
  (when (:assembly/id m)
    {:id (:assembly/id m) :unit-name (:assembly/unit-name m)
     :dimensional-tolerance-actual (:assembly/dimensional-tolerance-actual m)
     :dimensional-tolerance-min (:assembly/dimensional-tolerance-min m)
     :dimensional-tolerance-max (:assembly/dimensional-tolerance-max m)
     :ndt-defect-unresolved? (boolean (:assembly/ndt-defect-unresolved? m))
     :assembly-dispatched? (boolean (:assembly/assembly-dispatched? m))
     :airworthiness-certified? (boolean (:assembly/airworthiness-certified? m))
     :jurisdiction (:assembly/jurisdiction m) :status (:assembly/status m)
     :dispatch-number (:assembly/dispatch-number m) :evidence-number (:assembly/evidence-number m)}))

(defrecord DatomicStore [conn]
  Store
  (assembly [_ id]
    (pull->assembly (d/pull (d/db conn) assembly-pull [:assembly/id id])))
  (all-assemblies [_]
    (->> (d/q '[:find [?id ...] :where [?e :assembly/id ?id]] (d/db conn))
         (map #(pull->assembly (d/pull (d/db conn) assembly-pull [:assembly/id %])))
         (sort-by :id)))
  (ndt-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :ndt-screen/assembly-id ?aid] [?k :ndt-screen/payload ?p]]
              (d/db conn) id)))
  (requirements-verification-of [_ assembly-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/assembly-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) assembly-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (evidence-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :evidence/seq ?s] [?e :evidence/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-evidence-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :evidence-sequence/jurisdiction ?j] [?e :evidence-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (assembly-already-dispatched? [s assembly-id]
    (boolean (:assembly-dispatched? (assembly s assembly-id))))
  (assembly-already-certified? [s assembly-id]
    (boolean (:airworthiness-certified? (assembly s assembly-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :assembly/upsert
      (d/transact! conn [(assembly->tx value)])

      :verification/set
      (d/transact! conn [{:verification/assembly-id (first path) :verification/payload (enc payload)}])

      :ndt-screen/set
      (d/transact! conn [{:ndt-screen/assembly-id (first path) :ndt-screen/payload (enc payload)}])

      :assembly/mark-dispatched
      (let [assembly-id (first path)
            {:keys [result assembly-patch]} (dispatch-assembly! s assembly-id)
            jurisdiction (:jurisdiction (assembly s assembly-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(assembly->tx (assoc assembly-patch :id assembly-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :assembly/mark-certified
      (let [assembly-id (first path)
            {:keys [result assembly-patch]} (issue-airworthiness-evidence! s assembly-id)
            jurisdiction (:jurisdiction (assembly s assembly-id))
            next-n (inc (next-evidence-sequence s jurisdiction))]
        (d/transact! conn
                     [(assembly->tx (assoc assembly-patch :id assembly-id))
                      {:evidence-sequence/jurisdiction jurisdiction :evidence-sequence/next next-n}
                      {:evidence/seq (count (evidence-history s)) :evidence/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-assemblies [s assemblies]
    (when (seq assemblies) (d/transact! conn (mapv assembly->tx (vals assemblies)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:assemblies ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [assemblies]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-assemblies s assemblies))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo assembly set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
