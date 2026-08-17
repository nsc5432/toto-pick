import { useState } from "react";

import { ForwardPickList } from "@/widgets/forward-pick-list";
import { ForwardTestReport } from "@/widgets/forward-test-report";

/**
 * 전진 검증 화면. 성적을 위에, 근거가 되는 사전 픽 기록을 아래에 둔다 —
 * 정산을 실행하면 둘 다 갱신돼야 하므로 refreshKey를 페이지에서 들고 있는다.
 */
export function ForwardTestPage() {
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <>
      <ForwardTestReport
        refreshKey={refreshKey}
        onSettled={() => setRefreshKey((key) => key + 1)}
      />
      <ForwardPickList refreshKey={refreshKey} />
    </>
  );
}
