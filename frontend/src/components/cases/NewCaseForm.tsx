import { useState } from 'react';
import { casesApi } from '../../api/client';
import type { CreateCaseRequest } from '../../types';

interface NewCaseFormProps {
  onCreate: (c: { id: string }) => void;
}

export function NewCaseForm({ onCreate }: NewCaseFormProps) {
  const [open, setOpen] = useState(false);
  const [systemId, setSystemId] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const body: CreateCaseRequest = { systemId, description };
      const res = await casesApi.create(body);
      onCreate(res.data);
      setSystemId('');
      setDescription('');
      setOpen(false);
    } catch (e: any) {
      setError(e.response?.data?.message || 'No se pudo crear el caso.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="btn btn-secondary btn-sm"
      >
        + Crear caso
      </button>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center" role="dialog" aria-modal="true">
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-sm"
            onClick={() => setOpen(false)}
          />
          <div className="bg-white rounded-xl border border-gray-200 p-6 w-full max-w-md shadow-xl">
            <h3 className="mb-4 text-lg font-semibold text-gray-900">Nuevo caso</h3>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Sistema</label>
                <input
                  className="input w-full"
                  placeholder="ej. pagos"
                  value={systemId}
                  onChange={(e) => setSystemId(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Descripción del problema</label>
                <textarea
                  className="input w-full min-h-[80px]"
                  placeholder="Describe lo que está ocurriendo..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  required
                />
              </div>
              {error && (
                <div className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-100">{error}</div>
              )}
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  className="btn btn-ghost btn-sm"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="btn btn-primary btn-sm"
                >
                  {submitting ? 'Creando...' : 'Crear caso'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
