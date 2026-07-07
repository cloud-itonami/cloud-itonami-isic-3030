(ns aerospace.facts
  "Per-jurisdiction aircraft/aerospace airworthiness-certification
  regulatory catalog -- the G2-style spec-basis table the Aerospace
  Manufacturing Governor checks every requirements/verify proposal
  against ('did the advisor cite an OFFICIAL public source for this
  jurisdiction's airworthiness-certification requirements, or did it
  invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official civil-
  aviation airworthiness authority (see `:provenance`); they are a
  STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  CAE-simulation-report/CFD-verification-report/NDT-chain-of-custody-
  record/material-certification-record evidence set submitted in some
  form; `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any `:requirements/verify`
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省航空局 (JCAB, Japan Civil Aviation Bureau)"
          :legal-basis "航空法 (Civil Aeronautics Act) / 耐空性審査要領"
          :national-spec "耐空性基準に基づく設計・製造・検査要件"
          :provenance "https://www.mlit.go.jp/koku/koku_fr10_000003.html"
          :required-evidence ["CAEシミュレーション報告書 (CAE-simulation-report)"
                              "CFD検証報告書 (CFD-verification-report)"
                              "非破壊検査連鎖記録 (NDT-chain-of-custody-record)"
                              "材料証明記録 (material-certification-record)"]}
   "USA" {:name "United States"
          :owner-authority "Federal Aviation Administration (FAA)"
          :legal-basis "14 CFR Part 25 (Airworthiness Standards: Transport Category Airplanes)"
          :national-spec "Type-certification design, manufacturing and inspection requirements"
          :provenance "https://www.faa.gov/aircraft/air_cert"
          :required-evidence ["CAE-simulation-report"
                              "CFD-verification-report"
                              "NDT-chain-of-custody-record"
                              "Material-certification-record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "UK Civil Aviation Authority (UK CAA)"
          :legal-basis "Air Navigation Order 2016 / UK Reg (EU) No 748/2012 (retained)"
          :national-spec "UK type-certification and continuing-airworthiness requirements"
          :provenance "https://www.caa.co.uk/aircraft-register/airworthiness/"
          :required-evidence ["CAE-simulation-report"
                              "CFD-verification-report"
                              "NDT-chain-of-custody-record"
                              "Material-certification-record"]}
   "DEU" {:name "Germany"
          :owner-authority "Luftfahrt-Bundesamt (LBA) / European Union Aviation Safety Agency (EASA)"
          :legal-basis "EASA CS-25 (Certification Specifications for Large Aeroplanes)"
          :national-spec "Musterzulassungs- und Fertigungsüberwachungsanforderungen"
          :provenance "https://www.easa.europa.eu/en/document-library/certification-specifications/group/cs-25-large-aeroplanes"
          :required-evidence ["CAE-Simulationsbericht (CAE-simulation-report)"
                              "CFD-Verifizierungsbericht (CFD-verification-report)"
                              "ZfP-Rückverfolgbarkeitsnachweis (NDT-chain-of-custody-record)"
                              "Werkstoffzertifikat (material-certification-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch an
  assembly action or issue airworthiness evidence on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3030 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `aerospace.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
