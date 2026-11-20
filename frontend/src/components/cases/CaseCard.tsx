import type { Case } from '../../types';
import { CaseBadge, SeverityBadge, formatRelative } from './CaseBadge';

interface CaseCardProps {
  c: Case;
  onDelete?: (id: string) => void;
}

export function CaseCard({ c, onDelete }: CaseCardProps) {
  return (
    <article className="card border border-gray-200 p-4">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <span className="text-lg font-semibold text-gray-900">#{c.id.slice(0, 8)}</span>
          <p className="text-sm text-gray-600 line-clamp-1">{c.description}</p>
        </div>
        <div className="flex flex-col sm:flex-row items-end gap-2 md:items-center md:justify-between w-full md:w-auto">
          <div className="flex items-center gap-2">
            <CaseBadge status={c.status} />
            <SeverityBadge severity={c.severity} />
          </div>
          <div className="text-xs text-gray-500">{formatRelative(c.createdAt)}</div>
          {onDelete && (
            <button
              onClick={(e) => { e.stopPropagation(); onDelete(c.id); }}
              className="btn btn-xs btn-ghost text-red-600 hover:bg-red-50"
            >
              Eliminar
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
