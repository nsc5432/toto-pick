import { Navigate, Route, Routes } from "react-router-dom";

import { BaseballResultPage } from "@/pages/baseball-result";
import { ForwardTestPage } from "@/pages/forward-test";
import { PickResultPage } from "@/pages/pick-result";
import { ResearchPage } from "@/pages/research";
import { Sidebar } from "@/widgets/sidebar";

import "./App.css";

function App() {
  return (
    <div className="app-layout">
      <Sidebar />
      <main className="app-layout__content">
        <Routes>
          <Route path="/" element={<Navigate to="/baseball-results" replace />} />
          <Route path="/baseball-results" element={<BaseballResultPage />} />
          <Route path="/pick-results" element={<PickResultPage />} />
          <Route path="/research" element={<ResearchPage />} />
          <Route path="/forward-test" element={<ForwardTestPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;
