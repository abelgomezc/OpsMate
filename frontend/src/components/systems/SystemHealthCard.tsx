import type { SystemHealth } from '../../types';

const STATUS_COLOR: Record<string, string> = {
  UP: 'text-green-700 bg-green-50 border-green-100',
  DEGRADED: 'text-amber-700 bg-amber-50 border-amber-100',
  DOWN: 'text-red-700 bg-red-50 border-red-100',
  UNKNOWN: 'text-gray-600 bg-gray-50 border-gray-100',
};

export function SystemHealthCard({ health }: { health: SystemHealth }) {
  const statusCls = STATUS_COLOR[health.status] ?? STATUS_COLOR.UNKNOWN;

  return (
    <article className="card border border-gray-200 p-5">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="text-lg font-semibold text-gray-900">{health.systemId}</div>
          <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusCls}`}>
            {health.status}
          </span>
        </div>
        <div className="text-xs text-gray-500">
          {health.services.length} servicio{health.services.length !== 1 ? 's' : ''}
        </div>
      </div>
      <div className="mt-4 space-y-2">
        {health.services.map((s) => {
          const sCls = STATUS_COLOR[s.status] ?? STATUS_COLOR.UNKNOWN;
          return (
            <div key={s.service} className="flex items-center justify-between px-3 py-2 bg-gray-50 rounded border border-gray-100">
              <div className="text-sm text-gray-700">{s.service}</div>
              <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${sCls}`}>
                {s.status}
              </span>
              <span className="text-xs text-gray-500 ml-2">{s.message}</span>
            </div>
          );
        })}
      </div>
    </article>
  );
}
