import type { Case } from '../../types';
import { CaseBadge } from './CaseBadge';

interface CaseDetailHeaderProps {
  c: Case;
}

export function CaseDetailHeader({ c }: CaseDetailHeaderProps) {
  return (
    <section className="mb-6 card border border-gray-200 p-5">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="text-xl font-bold text-gray-900">#{c.id.slice(0, 8)}</span>
            <CaseBadge status={c.status} />
          </div>
          <p className="text-sm text-gray-600">{c.description}</p>
          <div className="flex items-center gap-3 text-xs text-gray-500 mt-2">
            <span>Sistema: {c.systemId}</span>
            <span>·</span>
            <span>Creado: {new Date(c.createdAt).toLocaleString('es-ES')}</span>
            {c.resolvedAt && (
              <>
                <span>·</span>
                <span>Resuelto: {new Date(c.resolvedAt).toLocaleString('es-ES')}</span>
              </>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2 w-full md:w-auto justify-end">
          <button
            className="btn btn-secondary btn-sm"
            disabled={c.status !== 'RESOLVED'}
            onClick={() => {}}
          >
            Exportar reporte
          </button>
        </div>
      </div>
    </section>
  );
}

export default CaseDetailHeader;
