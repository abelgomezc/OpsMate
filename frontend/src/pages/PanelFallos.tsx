import { useState } from 'react';
import { useScenarios } from '../api/hooks';
import { ScenarioButton } from '../components/sim/ScenarioButton';

export default function PanelFallos() {
  const { data: scenarios, loading, error, inject, clear } = useScenarios();
  const [injecting, setInjecting] = useState<string | false>(false);

  if (loading) {
    return <div className="flex h-screen items-center justify-center text-gray-500">Cargando escenarios...</div>;
  }
  if (error) {
    return (
      <div className="text-center py-20">
        <h2 className="text-lg font-semibold text-gray-900">Error al cargar escenarios</h2>
        <p className="text-sm text-gray-500">{error.message}</p>
      </div>
    );
  }

  const handleInject = async (id: string) => {
    setInjecting(id);
    try {
      await inject(id, {});
    } catch (e: any) {
      window.alert(`No se pudo inyectar: ${e.response?.data?.message || e.message}`);
    } finally {
      setInjecting(false);
    }
  };

  const handleClear = async () => {
    setInjecting(false);
    try {
      await clear();
    } catch (e: any) {
      window.alert(`No se pudo limpiar: ${e.response?.data?.message || e.message}`);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Panel de fallos (sim-env)</h1>
        <p className="text-sm text-gray-500">Inyecta y limpia escenarios de fallo en el entorno simulado.</p>
      </div>

      {scenarios.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <span className="text-4xl mb-3">⚠️</span>
          <h3 className="text-lg font-semibold text-gray-900">No hay escenarios disponibles</h3>
          <p className="text-sm text-gray-500">El entorno simulado no expuso escenarios.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {scenarios.map((s) => (
            <ScenarioButton
              key={s.id}
              scenario={s}
              active={s.active}
              onInject={() => handleInject(s.id)}
              onClear={handleClear}
              injecting={injecting === s.id}
            />
          ))}
        </div>
      )}

      <div className="mt-6 flex items-center gap-2">
        <button
          onClick={handleClear}
          disabled={injecting !== false}
          className="btn btn-ghost btn-sm text-red-600 hover:bg-red-50"
        >
          Limpiar todos los escenarios
        </button>
        {injecting !== false && (
          <span className="text-xs text-gray-500">Procesando…</span>
        )}
      </div>
    </div>
  );
}
