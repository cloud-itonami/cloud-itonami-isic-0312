(ns freshwater-fishing.governor
  "FreshwaterFishingOperationsGovernor -- independent compliance layer for
  freshwater (river/lake/inland-waters) fishing fleet back-office operations
  coordination. The Advisory-LLM has no notion of vessel registration, quota
  ceilings, or escalation conditions, so this MUST be a separate system able
  to reject a proposal and fall back to HOLD.

  This actor supports inland fishing fleet OPERATIONS COORDINATION -- it
  does NOT command vessel navigation, operate fishing gear, or make catch
  decisions. Those remain the vessel operator's exclusive human authority on
  the water, always.

  Unlike marine (EEZ) fishing, there is no maritime-zone or flag-state
  concept here -- vessels operate on a named inland water body (a river or
  lake) under a single jurisdiction's permit/quota regime. There is no
  per-zone quota split.

  Governance Rules (all HARD, no overrides):

  1. Vessel verification: vessel/permit record must be verified and
     registered before ANY action. :vessel-id must exist in store and have
     :status :active.

  2. Effect matches op: proposal :effect must match the ONE legitimate
     effect for the request :op (see `op->effect`).

  3. Effect only :propose: all proposals have `:effect :propose` only --
     this actor never commits direct vessel operations, navigation, or
     catch decisions, which remain exclusive human (vessel operator)
     authority.

  4. Blocked operations (structural, permanent hold, no human override):
     - Any proposal for vessel navigation or vessel-command authority.
     - Any proposal for a catch decision (target species, quota
       allocation, where/when to fish).
     - Any proposal modifying :vessel-id, :permit-number, or
       :vessel-status fields.
     - Any :log-catch-record proposal whose declared quantity would push
       the vessel's cumulative landed weight past its permit quota
       (quota-exceedance is a hard, permanent block -- not a soft
       escalation -- because it is a regulatory compliance breach, not a
       judgment call).

  5. Escalation rules (soft, human sign-off required, not a rejection):
     - :flag-safety-concern ALWAYS escalates immediately.
     - supply orders above cost threshold (default 10000 units) escalate.
     - low advisor confidence (< 0.6) escalates."
  (:require [freshwater-fishing.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 10000)

;; ----------------------------- checks -----------------------------

(def op->effect
  "The ONE legitimate :effect a proposal may declare for each op. This is
  the CLOSED allowlist of coordination operations this actor may ever
  propose -- no other op is legitimate, regardless of what an advisor
  generates."
  {:log-catch-record             :propose
   :schedule-vessel-maintenance  :propose
   :flag-safety-concern          :propose
   :order-supplies               :propose})

(defn- vessel-verification-violations
  "HARD: vessel/permit must be verified and active before any action."
  [{:keys [op vessel-id]} proposal st]
  (when (and op vessel-id)
    (let [vessel (store/vessel st vessel-id)]
      (cond
        (nil? vessel)
        [{:rule :vessel-not-found
          :detail "登録されていない船舶 ID での提案"}]
        (not= :active (:status vessel))
        [{:rule :vessel-inactive
          :detail "非アクティブ/未検証な船舶・許可証での操作提案"}]
        :else nil))))

(defn- effect-mismatch-violations
  "HARD, checked first: effect must match op."
  [{:keys [op]} proposal]
  (when-let [expected (op->effect op)]
    (when (not= expected (:effect proposal))
      [{:rule :effect-mismatch
        :detail (str "op " op " の提案は :effect " expected
                     " のはずが実際には " (:effect proposal) " になっている")}])))

(defn- only-propose-violations
  "HARD: all effects must be :propose (coordination only, no direct
  actuation, no vessel command)."
  [{:keys [op]} proposal]
  (when (and op (not= :propose (:effect proposal)))
    [{:rule :non-propose-effect
      :detail "この actor では :effect は :propose のみ。航行・操業の直接権限は常に船の操縦者に留める"}]))

(defn- forbidden-fields-violations
  "HARD: certain fields are never modifiable via proposals."
  [{:keys [op vessel-id]} proposal]
  (let [forbidden #{:vessel-id :permit-number :vessel-status}
        patch-fields (when-let [v (:value proposal)] (set (keys v)))
        found (seq (filter forbidden patch-fields))]
    (when found
      [{:rule :forbidden-field
        :detail (str "変更禁止フィールドが含まれている: " (vec found))}])))

(defn- blocked-operation-violations
  "HARD: certain operations are permanently blocked -- vessel navigation /
  command authority and catch decisions are the vessel operator's
  exclusive authority on the water and are never delegable to this actor."
  [{:keys [op]} _proposal]
  (when (contains? #{:navigate-vessel :command-vessel :decide-catch} op)
    [{:rule :blocked-operation
      :detail "この操作は船の操縦者の exclusive authority（航行・操船・漁獲判断）であり actor には委譲できません"}]))

(defn- quota-exceedance-violations
  "HARD, permanent block (no human override): a catch-record proposal that
  would push the vessel's cumulative landed weight past its permit quota
  is a regulatory compliance breach, not a judgment call. There is no
  maritime-zone (EEZ) split here -- one permit, one quota, one inland
  water body."
  [{:keys [op vessel-id]} proposal st]
  (when (= op :log-catch-record)
    (let [vessel (store/vessel st vessel-id)
          quota (:quota-kg vessel)
          landed (:landed-kg vessel 0)
          add (get-in proposal [:value :quantity-kg] 0)]
      (when (and quota (> (+ landed add) quota))
        [{:rule :quota-exceedance
          :detail (str "漁獲量 " add "kg を加えると累計 " (+ landed add)
                       "kg となり許可枠 " quota "kg を超過する。恒久的にブロック")}]))))

(defn- safety-concern-escalation-violations
  "SOFT: :flag-safety-concern ALWAYS escalates immediately to the vessel
  operator/crew. Covers vessel-safety, water-level (flash-flood / rapid
  rise), and weather concerns on rivers and lakes."
  [{:keys [op]} _proposal]
  (when (= op :flag-safety-concern)
    [{:rule :safety-concern-escalation
      :detail "安全懸念フラグ（船体・水位・天候）は必ず人間（操縦者・乗員）の確認で即時 escalate されます"}]))

(defn- supply-cost-escalation-violations
  "SOFT: supply orders above threshold escalate."
  [{:keys [op]} proposal]
  (when (= op :order-supplies)
    (let [cost (get-in proposal [:value :estimated-cost] 0)]
      (when (>= cost supply-cost-threshold)
        [{:rule :high-cost-escalation
          :detail (str "コスト " cost " 超過。人間の確認で escalate 必須")}]))))

(defn check
  "Censors an Advisor proposal against the freshwater fishing governor
   rules. Returns {:ok? bool :violations [..] :soft-flags [..]
   :confidence c :escalate? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (effect-mismatch-violations request proposal)
                           (vessel-verification-violations request proposal st)
                           (only-propose-violations request proposal)
                           (forbidden-fields-violations request proposal)
                           (blocked-operation-violations request proposal)
                           (quota-exceedance-violations request proposal st)))
        soft (into []
                   (concat (safety-concern-escalation-violations request proposal)
                           (supply-cost-escalation-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        hard? (boolean (seq hard))
        soft? (boolean (seq soft))]
    {:ok?          (and (not hard?) (not low?) (not soft?))
     :violations   hard
     :soft-flags   soft
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? soft?))}))

(defn hold-fact
  "Audit fact for a rejected proposal (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :vessel-id  (:vessel-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :soft-flags (:soft-flags verdict)
   :confidence (:confidence verdict)})
