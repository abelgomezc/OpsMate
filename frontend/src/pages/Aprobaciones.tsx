import { usePendingApprovals } from '../api/hooks';
import { approvalsApi } from '../api/client';
import type { Approval } from '../types';
import { ApprovalCard } from '../components/approvals/ApprovalCard';

export default function Aprobaciones() {
  const { data: approvals, loading, error, refetch } = usePendingApprovals();

  const handleDecide = async (a: Approval, approve: boolean) => {
    const reason = window.prompt('Motivo de la decisión:', '');
    if (reason === null || reason.trim() === '') {
      window.alert('Se requiere un motivo.');
      return;
    }
    try {
      const body = { decidedBy: 'operador', reason: reason.trim() };
      if (approve) {
        await approvalsApi.approve(a.caseId, body);
      } else {
        await approvalsApi.reject(a.caseId, body);
      }
      refetch();
    } catch (e: any) {
      window.alert(`No se pudo ${approve ? 'aprobar' : 'rechazar'}: ${e.response?.data?.message || e.message}`);
    }
  };

  if (loading) {
    return <div className="flex h-screen items-center justify-center text-gray-500">Cargando aprobaciones...</div>;
  }
  if (error) {
    return (
      <div className="text-center py-20">
        <h2 className="text-lg font-semibold text-gray-900">Error al cargar aprobaciones</h2>
        <p className="text-sm text-gray-500">{error.message}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Aprobaciones pendientes</h1>
          <p className="text-sm text-gray-500">Decisiones sobre acciones de remediación de alto riesgo.</p>
        </div>
        <button
          onClick={refetch}
          disabled={loading}
          className="btn btn-secondary btn-sm"
        >
          Actualizar
        </button>
      </div>

      {approvals.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <span className="text-4xl mb-3">✅</span>
          <h3 className="text-lg font-semibold text-gray-900">No hay aprobaciones pendientes</h3>
          <p className="text-sm text-gray-500">Todas las acciones de alto riesgo han sido decididas.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {approvals.map((a) => (
            <ApprovalCard key={a.id} a={a} onDecide={(approve) => handleDecide(a, approve)} />
          ))}
        </div>
      )}
    </div>
  );
}
