import type { ScenarioInfo } from '../../types';

const SCENARIO_LABELS: Record<string, string> = {
  service_down: 'Servicio caído',
  high_latency: 'Alta latencia',
  error_spike: 'Pico de errores',
  stuck_queue: 'Cola atascada',
  db_pool_exhausted: 'Pool de DB agotado',
  cascading_failure: 'Falla en cascada',
};

export function ScenarioButton({
  scenario,
  active,
  onInject,
  onClear,
  injecting,
}: {
  scenario: ScenarioInfo;
  active: boolean;
  onInject: () => void;
  onClear: () => void;
  injecting: boolean;
}) {
  const label = SCENARIO_LABELS[scenario.id] ?? scenario.id;
  const isActive = active || scenario.active;

  return (
    <article className="card border border-gray-200 p-4">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="text-lg">{isActive ? '🔴' : '⚪'}</span>
          <div>
            <div className="text-sm font-semibold text-gray-900">{label}</div>
            <div className="text-xs text-gray-500">{scenario.description}</div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {isActive ? (
            <button
              disabled={injecting}
              onClick={onClear}
              className="btn btn-ghost btn-sm text-red-600 hover:bg-red-50"
            >
              Limpiar
            </button>
          ) : (
            <button
              disabled={injecting}
              onClick={onInject}
              className="btn btn-primary btn-sm"
            >
              Inyectar
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
