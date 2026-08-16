import { useState } from "react";

import { AlgorithmKpiDashboard } from "@/widgets/algorithm-kpi-dashboard";
import { PickResultGrid } from "@/widgets/pick-result-grid";
import { PickSimulationPanel } from "@/widgets/pick-simulation";
import type { KpiQuery } from "@/widgets/pick-simulation";
import { PickSlipList } from "@/widgets/pick-slip-list";

export function PickResultPage() {
  const [query, setQuery] = useState<KpiQuery | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleQuery = (nextQuery: KpiQuery, refresh: boolean) => {
    setQuery(nextQuery);
    if (refresh) {
      setRefreshKey((k) => k + 1);
    }
  };

  return (
    <>
      <PickSimulationPanel onQuery={handleQuery} />
      {query && <AlgorithmKpiDashboard query={query} refreshKey={refreshKey} />}
      {query && <PickSlipList query={query} refreshKey={refreshKey} />}
      <PickResultGrid key={refreshKey} />
    </>
  );
}
