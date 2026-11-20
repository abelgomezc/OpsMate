const STATE_LABELS: Record<string, string> = {
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

const SEVERITY_LABELS: Record<string, string> = {
  BAJA: 'Baja',
  MEDIA: 'Media',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
};

export function CaseBadge({ status }: { status: string }) {
  const cls = `badge badge-${status.toLowerCase()}`;
  return (
    <span className={cls} title={STATE_LABELS[status] ?? status}>
      {STATE_LABELS[status] ?? status}
    </span>
  );
}

export function SeverityBadge({ severity }: { severity: string | null }) {
  if (!severity) return <span className="text-xs text-gray-400">—</span>;
  const label = SEVERITY_LABELS[severity] ?? severity;
  const cls = `badge badge-${label.toLowerCase()}`;
  return <span className={cls}>{label}</span>;
}

export function formatDate(iso: string | null | undefined) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

export function formatRelative(iso: string | null | undefined) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'ahora';
    if (diffMin < 60) return `hace ${diffMin} min`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24) return `hace ${diffH} h`;
    return `hace ${Math.floor(diffH / 24)} d`;
  } catch {
    return iso;
  }
}
