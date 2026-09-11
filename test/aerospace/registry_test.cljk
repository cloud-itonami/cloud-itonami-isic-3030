(ns aerospace.registry-test
  (:require [clojure.test :refer [deftest is]]
            [aerospace.registry :as r]))

;; ----------------------------- assembly-tolerance-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/assembly-tolerance-out-of-range? {:dimensional-tolerance-actual 0.05 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10})))
  (is (not (r/assembly-tolerance-out-of-range? {:dimensional-tolerance-actual -0.10 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10})))
  (is (not (r/assembly-tolerance-out-of-range? {:dimensional-tolerance-actual 0.10 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/assembly-tolerance-out-of-range? {:dimensional-tolerance-actual -0.35 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10}))
  (is (r/assembly-tolerance-out-of-range? {:dimensional-tolerance-actual 0.35 :dimensional-tolerance-min -0.10 :dimensional-tolerance-max 0.10})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/assembly-tolerance-out-of-range? {})))
  (is (not (r/assembly-tolerance-out-of-range? {:dimensional-tolerance-actual 0.35}))))

;; ----------------------------- register-assembly-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-assembly-dispatch "assembly-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-assembly-dispatch "assembly-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-DSP-000007"))
    (is (= (get-in result ["record" "assembly_id"]) "assembly-1"))
    (is (= (get-in result ["record" "kind"]) "assembly-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-assembly-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-assembly-dispatch "assembly-1" "" 0)))
  (is (thrown? Exception (r/register-assembly-dispatch "assembly-1" "JPN" -1))))

;; ----------------------------- register-airworthiness-evidence -----------------------------

(deftest evidence-is-a-draft-not-real-certification
  (let [result (r/register-airworthiness-evidence "assembly-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest evidence-assigns-evidence-number
  (let [result (r/register-airworthiness-evidence "assembly-1" "JPN" 3)]
    (is (= (get result "evidence_number") "JPN-AWE-000003"))
    (is (= (get-in result ["record" "assembly_id"]) "assembly-1"))
    (is (= (get-in result ["record" "kind"]) "airworthiness-evidence-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest evidence-validation-rules
  (is (thrown? Exception (r/register-airworthiness-evidence "" "JPN" 0)))
  (is (thrown? Exception (r/register-airworthiness-evidence "assembly-1" "" 0)))
  (is (thrown? Exception (r/register-airworthiness-evidence "assembly-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-assembly-dispatch "assembly-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-assembly-dispatch "assembly-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DSP-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DSP-000001" (get-in hist2 [1 "record_id"])))))
