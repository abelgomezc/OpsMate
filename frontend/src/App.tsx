import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Sidebar, TopBar } from './components/layout/Sidebar';
import Casos from './pages/Casos';
import DetalleCaso from './pages/DetalleCaso';
import Aprobaciones from './pages/Aprobaciones';
import Sistemas from './pages/Sistemas';
import PanelFallos from './pages/PanelFallos';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-gray-50">
        <Sidebar />
        <div className="ml-64">
          <TopBar onNewCase={() => {}} />
          <main className="p-6">
            <Routes>
              <Route path="/casos" element={<Casos />} />
              <Route path="/casos/:id" element={<DetalleCaso />} />
              <Route path="/aprobaciones" element={<Aprobaciones />} />
              <Route path="/sistemas" element={<Sistemas />} />
              <Route path="/panel-fallos" element={<PanelFallos />} />
              <Route index element={<Casos />} />
            </Routes>
          </main>
        </div>
      </div>
    </BrowserRouter>
  );
}
