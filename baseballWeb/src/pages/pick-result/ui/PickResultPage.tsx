import { useState } from "react";

import { PickResultGrid } from "@/widgets/pick-result-grid";
import { PickSimulationPanel } from "@/widgets/pick-simulation";

export function PickResultPage() {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <>
      <PickSimulationPanel onCompleted={() => setRefreshKey((k) => k + 1)} />
      <PickResultGrid key={refreshKey} />
    </>
  );
}
