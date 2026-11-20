import type { CaseEvent } from '../../types';

interface CaseTimelineProps {
  events: CaseEvent[];
}

export function CaseTimeline({ events }: CaseTimelineProps) {
  const sorted = [...events].sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  return (
    <section className="card border border-gray-200 p-5">
      <div className="flex items-center gap-2 mb-4">
        <span className="text-sm font-semibold text-gray-900">Cronología en vivo</span>
        <span className="ml-auto text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">
          {sorted.length} eventos
        </span>
      </div>
      <div className="relative space-y-4">
        <div className="absolute left-2.5 top-0 bottom-0 w-0.5 bg-gray-200" />
        {sorted.map((ev, i) => {
          const payloadStr = JSON.stringify(ev.payload, null, 2);
          return (
            <div key={ev.id} className="relative pl-8">
              <div className="absolute -left-[9px] top-1 w-3 h-3 rounded-full border-2 border-blue-500 bg-white z-10" />
              <div className="text-xs font-medium text-gray-500 mb-1">
                {ev.type}
                <span className="ml-2 text-gray-400">{new Date(ev.createdAt).toLocaleString('es-ES')}</span>
              </div>
              <div className="bg-gray-50 rounded border border-gray-100 p-3 text-sm text-gray-700 font-mono overflow-x-auto">
                {payloadStr}
              </div>
              {i < sorted.length - 1 && (
                <div className="absolute left-1.5 top-[36px] bottom-[calc(100%+16px)] w-0.5 bg-gray-200" />
              )}
            </div>
          );
        })}
        {sorted.length === 0 && (
          <div className="text-center py-8 text-sm text-gray-500">Sin eventos aún.</div>
        )}
      </div>
    </section>
  );
}
