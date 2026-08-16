import { useState } from "react";

import { AlgorithmSearchPanel } from "@/widgets/algorithm-search-panel";
import { ExperimentLeaderboard } from "@/widgets/experiment-leaderboard";

export function ResearchPage() {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <>
      <AlgorithmSearchPanel onSearched={() => setRefreshKey((key) => key + 1)} />
      <ExperimentLeaderboard refreshKey={refreshKey} />
    </>
  );
}
