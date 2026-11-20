import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCases } from '../api/hooks';
import { NewCaseForm } from '../components/cases/NewCaseForm';
import { CaseList } from '../components/cases/CaseList';

const STATUS_OPTIONS: string[] = [
  'NEW', 'TRIAGING', 'INVESTIGATING', 'PLANNED', 'AWAITING_APPROVAL',
  'EXECUTING', 'VERIFYING', 'RESOLVED', 'FAILED', 'CANCELLED',
];

const STATUS_LABELS: Record<string, string> = {
  NEW: 'Nuevo',
  TRIAGING: 'Clasificando',
  INVESTIGATING: 'Investigando',
  PLANNED: 'Planificado',
  AWAITING_APPROVAL: 'Esperando aprobación',
  EXECUTING: 'Ejecutando',
  VERIFYING: 'Verificando',
  RESOLVED: 'Resuelto',
  FAILED: 'Fallido',
  CANCELLED: 'Cancelado',
};

export default function Casos() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<string[]>([]);
  const [systemFilter, setSystemFilter] = useState<string>('');
  const [severityFilter, setSeverityFilter] = useState<string>('');

  const { data: cases, loading, error, refetch } = useCases({
    status: statusFilter.length ? statusFilter : undefined,
    systemId: systemFilter || undefined,
    severity: severityFilter || undefined,
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Casos</h1>
          <p className="text-sm text-gray-500">Gestiona y supervisa los casos de remediación.</p>
        </div>
        <div className="flex items-center gap-2">
          <NewCaseForm onCreate={() => refetch()} />
        </div>
      </div>

      <div className="flex flex-wrap gap-3 bg-gray-50 rounded-xl border border-gray-200 p-4">
        <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Estado</label>
        <select
          multiple
          size={4}
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(Array.from(e.target.selectedOptions, (o) => o.value));
          }}
          className="input bg-white text-sm min-w-[150px]"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{STATUS_LABELS[s]}</option>
          ))}
        </select>
        <label className="text-xs font-medium text-gray-500 uppercase tracking-wider hidden md:block">Sistema</label>
        <input
          placeholder="ID del sistema"
          value={systemFilter}
          onChange={(e) => setSystemFilter(e.target.value)}
          className="input text-sm hidden md:block w-32"
        />
        <label className="text-xs font-medium text-gray-500 uppercase tracking-wider hidden md:block">Severidad</label>
        <input
          placeholder="ej. ALTA"
          value={severityFilter}
          onChange={(e) => setSeverityFilter(e.target.value)}
          className="input text-sm hidden md:block w-24"
        />
        <button
          onClick={refetch}
          disabled={loading}
          className="btn btn-secondary btn-sm hidden md:block"
        >
          {loading ? 'Cargando...' : 'Actualizar'}
        </button>
      </div>

      {loading && <div className="h-64 flex items-center justify-center text-gray-500">Cargando casos...</div>}
      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700 border border-red-100">
          Error al cargar casos: {error.message}
        </div>
      )}
      {!loading && !error && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wider">
              {cases.length} caso{cases.length !== 1 ? 's' : ''}
            </h2>
          </div>
          <CaseList
            cases={cases}
            onDelete={() => {}}
            onSelect={(id) => navigate(`/casos/${id}`)}
          />
        </div>
      )}
    </div>
  );
}
