import { useState } from 'react';
import { useSystems, useSystemHealth, useMonitor } from '../api/hooks';
import { SystemHealthCard } from '../components/systems/SystemHealthCard';

export default function Sistemas() {
  const { data: systems, loading, error, refetch } = useSystems();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { data: health, loading: healthLoading } = useSystemHealth(selectedId);
  const { data: monitor, start, stop, setInterval, loading: monitorLoading } = useMonitor();

  if (loading) {
    return <div className="flex h-screen items-center justify-center text-gray-500">Cargando sistemas...</div>;
  }
  if (error) {
    return (
      <div className="text-center py-20">
        <h2 className="text-lg font-semibold text-gray-900">Error al cargar sistemas</h2>
        <p className="text-sm text-gray-500">{error.message}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Sistemas</h1>
          <p className="text-sm text-gray-500">Salud de cada servicio registrado y control del monitor automático.</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={refetch} disabled={loading} className="btn btn-secondary btn-sm">
            Actualizar
          </button>
        </div>
      </div>

      <section className="card border border-gray-200 p-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-gray-900">Monitor automático</span>
            <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold ${monitor?.enabled ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
              {monitor?.enabled ? 'Activado' : 'Desactivado'}
            </span>
          </div>
          <div className="flex items-center gap-2 text-xs text-gray-600">
            <span>Intervalo: {monitor?.intervalSeconds ?? 0}s</span>
            {monitor?.lastCheck && (
              <span className="ml-2">Última comprobación: {new Date(monitor.lastCheck).toLocaleString('es-ES')}</span>
            )}
          </div>
          <div className="flex items-center gap-2 w-full md:w-auto">
            <input
              id="interval-input"
              type="number"
              min="1"
              defaultValue={monitor?.intervalSeconds ?? 30}
              className="input w-20 text-sm"
            />
            <button
              onClick={() => {
                const el = document.getElementById('interval-input');
                const secs = Number((el as HTMLInputElement)?.value);
                if (secs > 0) setInterval(secs);
              }}
              disabled={monitorLoading}
              className="btn btn-primary btn-sm"
            >
              Aplicar intervalo
            </button>
            <button
              onClick={monitor?.enabled ? stop : start}
              disabled={monitorLoading}
              className={`btn btn-sm ${monitor?.enabled ? 'btn-danger' : 'btn-success'}`}
            >
              {monitor?.enabled ? 'Desactivar' : 'Activar'}
            </button>
          </div>
        </div>
      </section>

      <section className="card border border-gray-200 p-5">
        <h2 className="mb-4 text-sm font-semibold text-gray-900 uppercase tracking-wider">Salud de sistemas</h2>
        {systems.length === 0 ? (
          <p className="text-sm text-gray-500">No hay sistemas registrados.</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {systems.map((s) => (
              <div
                key={s.id}
                onClick={() => setSelectedId(s.id)}
                className="cursor-pointer hover:bg-gray-50 transition-colors p-3 rounded border border-transparent"
              >
                <SystemHealthCard health={{ systemId: s.id, status: 'UP', services: [] }} />
              </div>
            ))}
          </div>
        )}
      </section>

      {selectedId && health && (
        <section className="card border border-gray-200 p-5">
          <div className="flex items-center gap-2 mb-4">
            <button onClick={() => setSelectedId(null)} className="text-sm text-gray-500 hover:underline">← Volver</button>
            <h2 className="text-sm font-semibold text-gray-900 ml-auto">Salud de {selectedId}</h2>
          </div>
          <SystemHealthCard health={health} />
          {healthLoading && <p className="mt-3 text-xs text-gray-500">Cargando salud...</p>}
        </section>
      )}
    </div>
  );
}
