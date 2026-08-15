import { Navigate, Route, Routes } from "react-router-dom";

import { BaseballResultPage } from "@/pages/baseball-result";
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
        </Routes>
      </main>
    </div>
  );
}

export default App;
