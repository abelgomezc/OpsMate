import type { Approval } from '../../types';

const RISK_COLOR: Record<string, string> = {
  LOW: 'text-green-700 bg-green-50 border-green-100',
  MEDIUM: 'text-amber-700 bg-amber-50 border-amber-100',
  HIGH: 'text-red-700 bg-red-50 border-red-100',
};

export function ApprovalCard({ a, onDecide }: { a: Approval; onDecide: (approve: boolean) => void }) {
  const statusLabel: Record<string, string> = {
    PENDING: 'Pendiente',
    APPROVED: 'Aprobado',
    REJECTED: 'Rechazado',
  };
  const riskCls = RISK_COLOR[a.riskLevel] ?? 'text-gray-700 bg-gray-50 border-gray-100';
  const decided = a.status !== 'PENDING';

  return (
    <article className={`card border border-gray-200 p-5 ${decided ? 'bg-green-50/50' : 'bg-white'}`}>
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <div className="text-sm font-semibold text-gray-900">{a.actionName}</div>
          <div className="text-xs text-gray-500">Caso #{a.caseId.slice(0, 8)} · {new Date(a.requestedAt).toLocaleString('es-ES')}</div>
        </div>
        <div className="flex items-center gap-3">
          <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${riskCls}`}>
            {a.riskLevel}
          </span>
          <span className={`text-xs font-medium ${decided ? 'text-green-700' : 'text-gray-600'}`}>
            {statusLabel[a.status]}
          </span>
        </div>
      </div>
      {a.reason && (
        <div className="mt-3 text-sm text-gray-600">
          <span className="font-semibold text-gray-800">Motivo:</span> {a.reason}
        </div>
      )}
      {!decided && (
        <div className="mt-4 flex items-center gap-2">
          <button
            onClick={() => onDecide(true)}
            className="btn btn-success btn-sm"
          >
            Aprobar
          </button>
          <button
            onClick={() => onDecide(false)}
            className="btn btn-danger btn-sm"
          >
            Rechazar
          </button>
        </div>
      )}
    </article>
  );
}
