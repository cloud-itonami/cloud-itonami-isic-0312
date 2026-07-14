(ns freshwater-fishing.llm-advisor
  "Freshwater Fishing Operations Advisor (LLM interface). The advisor
  generates proposals for fleet coordination operations, all routed
  through the governor before commitment. No external service calls in the
  base module -- callers inject a real LLM or a mock.

  The advisor NEVER proposes vessel navigation, fishing-gear operation, or
  catch decisions -- those are outside the closed op allowlist
  (`freshwater-fishing.governor/op->effect`) and would be rejected as a
  blocked operation even if generated.")

(defprotocol Advisor
  "An LLM advisor that generates proposals for freshwater fishing fleet
  operations."
  (-advise [advisor store request]
    "Generates a proposal from the request. Returns
     {:effect :propose :value {...} :cites [...] :summary ... :confidence ...}"))

(def mock-advisor-impl
  "A mock advisor for testing. Always generates :propose effects with
  high confidence."
  (reify Advisor
    (-advise [_advisor _store request]
      (let [{:keys [op vessel-id]} request]
        {:effect :propose
         :op op
         :vessel-id vessel-id
         :value {:op op}
         :cites ["mock-basis"]
         :summary (str "Mock proposal for " op " at " vessel-id)
         :confidence 0.9}))))

(defn mock-advisor []
  mock-advisor-impl)

(defn advise [advisor store request]
  (-advise advisor store request))

(defn trace
  "Audit trace for an advisory call."
  [request proposal]
  {:t :advisor-trace
   :op (:op request)
   :vessel-id (:vessel-id request)
   :confidence (:confidence proposal)
   :summary (:summary proposal)})
