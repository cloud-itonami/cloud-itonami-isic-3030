(ns aerospace.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/dispatch-assembly`/`:actuation/issue-
  airworthiness-evidence` must NEVER be a member of any phase's
  `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [aerospace.phase :as phase]))

(deftest dispatch-assembly-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real robot assembly dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/dispatch-assembly))
          (str "phase " n " must not auto-commit :actuation/dispatch-assembly")))))

(deftest issue-airworthiness-evidence-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits real airworthiness evidence"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-airworthiness-evidence))
          (str "phase " n " must not auto-commit :actuation/issue-airworthiness-evidence")))))

(deftest ndt-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :ndt/screen))
          (str "phase " n " must not auto-commit :ndt/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":assembly/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:assembly/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :assembly/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/dispatch-assembly} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-airworthiness-evidence} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :assembly/intake} :commit)))))
