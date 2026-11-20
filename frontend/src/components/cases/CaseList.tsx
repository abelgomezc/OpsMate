import { useState } from 'react';
import { casesApi } from '../../api/client';
import type { Case } from '../../types';
import { CaseBadge, SeverityBadge, formatRelative } from './CaseBadge';

interface CaseListProps {
  cases: Case[];
  onDelete: (id: string) => void;
  onSelect: (id: string) => void;
}

export function CaseList({ cases, onDelete, onSelect }: CaseListProps) {
  const [deleting, setDeleting] = useState<string | null>(null);

  const handleDelete = async (id: string) => {
    if (!window.confirm('¿Eliminar este caso?')) return;
    setDeleting(id);
    try {
      await casesApi.remove(id);
      onDelete(id);
    } catch (e) {
      alert('No se pudo eliminar el caso.');
    } finally {
      setDeleting(null);
    }
  };

  if (cases.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <span className="text-4xl mb-3">📭</span>
        <h3 className="text-lg font-semibold text-gray-900">No hay casos</h3>
        <p className="text-sm text-gray-500">Crea un caso nuevo para comenzar.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {cases.map((c) => (
        <article
          key={c.id}
          className="card border border-gray-200 p-4 hover:bg-gray-50 cursor-pointer transition-colors group"
          onClick={() => onSelect(c.id)}
        >
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
            <div className="flex flex-col gap-1">
              <div className="text-lg font-semibold text-gray-900">#{c.id.slice(0, 8)}</div>
              <p className="text-sm text-gray-600 line-clamp-1">{c.description}</p>
              {c.systemId && (
                <p className="text-xs text-gray-500 mt-1">Sistema: {c.systemId}</p>
              )}
            </div>
            <div className="flex flex-col sm:flex-row items-end gap-2 md:items-center md:justify-between w-full md:w-auto">
              <div className="flex items-center gap-2">
                <CaseBadge status={c.status} />
                <SeverityBadge severity={c.severity} />
              </div>
              <div className="text-xs text-gray-500">{formatRelative(c.createdAt)}</div>
              <button
                disabled={deleting === c.id}
                onClick={(e) => { e.stopPropagation(); handleDelete(c.id); }}
                className="btn btn-xs btn-ghost text-red-600 hover:bg-red-50 opacity-50 pointer-events-none"
              >
                Eliminar
              </button>
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}

export function EmptyCases() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <span className="text-4xl mb-3">📭</span>
      <h3 className="text-lg font-semibold text-gray-900">No hay casos</h3>
      <p className="text-sm text-gray-500">Crea un caso nuevo para comenzar.</p>
    </div>
  );
}
