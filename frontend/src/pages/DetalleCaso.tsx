import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCase, useExportCaseReport } from '../api/hooks';
import { CaseBadge, formatDate } from '../components/cases/CaseBadge';
import { CaseTimeline } from '../components/cases/CaseTimeline';

export default function DetalleCaso() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const eventSourceRef = useRef<EventSource | null>(null);
  const [rawEvents, setRawEvents] = useState<Array<{ type: string; payload: unknown; createdAt: string }>>([]);
  const [connected, setConnected] = useState(false);

  const { data: c, loading, error } = useCase(id ?? null);
  const { loading: exportLoading, exportReport } = useExportCaseReport(id ?? '');

  useEffect(() => {
    if (!id) return;
    const es = new EventSource(`/api/cases/${id}/events`);
    eventSourceRef.current = es;
    es.onopen = () => setConnected(true);
    es.onerror = () => {
      setConnected(false);
      es.close();
    };
    es.onmessage = (evt) => {
      try {
        const data = JSON.parse(evt.data) as { type: string; caseId?: string; payload?: unknown; createdAt?: string };
        setRawEvents((prev) => [...prev, {
          type: data.type,
          payload: data.payload ?? {},
          createdAt: data.createdAt ?? new Date().toISOString(),
        }]);
      } catch {
        // ignore non-json messages
      }
    };
    return () => {
      es.close();
      eventSourceRef.current = null;
      setConnected(false);
    };
  }, [id]);

  const events = rawEvents.map((e, i) => ({
    id: i,
    caseId: id ?? '',
    type: e.type,
    payload: typeof e.payload === 'object' && e.payload !== null ? e.payload as Record<string, unknown> : {},
    createdAt: e.createdAt,
  }));

  const handleExport = async () => {
    const md = await exportReport();
    if (!md) return;
    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `caso-${(id ?? '').slice(0, 8)}-reporte.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center text-gray-500">Cargando caso...</div>
    );
  }
  if (error || !c) {
    return (
      <div className="text-center py-20">
        <h2 className="text-lg font-semibold text-gray-900">Caso no encontrado</h2>
        <p className="text-sm text-gray-500">{error?.message ?? 'El caso no existe.'}</p>
        <button
          onClick={() => navigate('/casos')}
          className="mt-4 btn btn-primary btn-sm"
        >
          Volver a casos
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <a href="/casos" className="text-sm text-gray-500 hover:underline">← Volver</a>
            <span className="text-2xl font-bold text-gray-900">#{c.id.slice(0, 8)}</span>
            <CaseBadge status={c.status} />
          </div>
          <p className="text-sm text-gray-600">{c.description}</p>
          <div className="flex items-center gap-3 text-xs text-gray-500 mt-2">
            <span>Sistema: {c.systemId}</span>
            <span>·</span>
            <span>Creado: {formatDate(c.createdAt)}</span>
            {c.resolvedAt && <><span>·</span> <span>Resuelto: {formatDate(c.resolvedAt)}</span></>}
          </div>
        </div>
        <div className="flex items-center gap-2 w-full md:w-auto justify-end">
          <button
            onClick={handleExport}
            disabled={exportLoading || c.status !== 'RESOLVED'}
            className="btn btn-secondary btn-sm"
          >
            {exportLoading ? 'Exportando...' : 'Exportar reporte Markdown'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-8 space-y-6">
          {c.plan && (
            <section className="card border border-gray-200 p-5">
              <h3 className="mb-3 text-sm font-semibold text-gray-900 uppercase tracking-wider">Plan de remediación</h3>
              <div className="bg-gray-50 rounded border border-gray-100 p-4 text-sm text-gray-700 whitespace-pre-wrap font-mono">
                {c.plan}
              </div>
            </section>
          )}
          {c.proposedAction && (
            <section className="card border border-gray-200 p-5">
              <h3 className="mb-3 text-sm font-semibold text-gray-900 uppercase tracking-wider">Acción propuesta</h3>
              <div className="bg-blue-50 rounded border border-blue-100 p-4 text-sm text-gray-800">
                {c.proposedAction}
              </div>
            </section>
          )}
          {c.executedAction && (
            <section className="card border border-gray-200 p-5">
              <h3 className="mb-3 text-sm font-semibold text-gray-900 uppercase tracking-wider">Acción ejecutada</h3>
              <div className="bg-green-50 rounded border border-green-100 p-4 text-sm text-gray-800">
                {c.executedAction}
              </div>
              {c.actionResult && (
                <div className="mt-3 text-xs text-gray-600 font-mono bg-gray-100 p-2 rounded">
                  {c.actionResult}
                </div>
              )}
            </section>
          )}
          {c.failReason && (
            <section className="card border-red-200 bg-red-50 p-5">
              <h3 className="mb-3 text-sm font-semibold text-red-900 uppercase tracking-wider">Motivo de fallo</h3>
              <div className="text-sm text-red-700">{c.failReason}</div>
            </section>
          )}
        </div>

        <div className="lg:col-span-4 space-y-6">
          <section className="card border border-gray-200 p-5">
            <div className="flex items-center gap-2 mb-4">
              <span className="text-sm font-semibold text-gray-900">Estado de conexión</span>
              <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-bold ${connected ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                {connected ? 'En vivo' : 'Desconectado'}
              </span>
            </div>
            <CaseTimeline events={events} />
          </section>
        </div>
      </div>
    </div>
  );
}
