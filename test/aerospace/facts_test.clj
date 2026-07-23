(ns aerospace.facts-test
  (:require [clojure.test :refer [deftest is]]
            [aerospace.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest fra-has-a-spec-basis
  (is (some? (facts/spec-basis "FRA")))
  (is (string? (:provenance (facts/spec-basis "FRA")))))

(deftest bra-has-a-spec-basis
  (is (some? (facts/spec-basis "BRA")))
  (is (string? (:provenance (facts/spec-basis "BRA")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR" "FRA"])]
    (is (= 3 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["FRA" "GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest fra-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "FRA")]
    (is (seq all))
    (is (facts/required-evidence-satisfied? "FRA" all))
    (is (not (facts/required-evidence-satisfied? "FRA" (rest all))))))

(deftest bra-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "BRA")]
    (is (seq all))
    (is (facts/required-evidence-satisfied? "BRA" all))
    (is (not (facts/required-evidence-satisfied? "BRA" (rest all))))))
