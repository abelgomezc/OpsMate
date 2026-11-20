export type CaseState =
  | 'NEW'
  | 'TRIAGING'
  | 'INVESTIGATING'
  | 'PLANNED'
  | 'AWAITING_APPROVAL'
  | 'EXECUTING'
  | 'VERIFYING'
  | 'RESOLVED'
  | 'FAILED'
  | 'CANCELLED';

export type Severity = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type SystemStatus = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';

export type ScenarioId =
  | 'service_down'
  | 'high_latency'
  | 'error_spike'
  | 'stuck_queue'
  | 'db_pool_exhausted'
  | 'cascading_failure';

export interface Case {
  id: string;
  systemId: string;
  status: CaseState;
  severity: string | null;
  category: string | null;
  priority: string | null;
  description: string;
  plan: string | null;
  proposedAction: string | null;
  executedAction: string | null;
  actionResult: string | null;
  resolved: boolean;
  failReason: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  timeoutAt: string | null;
}

export interface CreateCaseRequest {
  systemId: string;
  description: string;
}

export interface CaseEvent {
  id: number;
  caseId: string;
  type: string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface StreamEvent {
  type: string;
  caseId?: string;
  [key: string]: unknown;
}

export interface Approval {
  id: string;
  caseId: string;
  actionName: string;
  riskLevel: RiskLevel;
  requestedAt: string;
  status: ApprovalStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  reason: string | null;
  createdAt: string;
}

export interface ApprovalDecision {
  decidedBy: string;
  reason: string;
}

export interface SystemSummary {
  id: string;
  name: string;
  connectorType: string;
  active: boolean;
  baseUrl: string | null;
  services: string[];
}

export interface ServiceHealth {
  service: string;
  status: string;
  message: string;
}

export interface SystemHealth {
  systemId: string;
  status: SystemStatus;
  services: ServiceHealth[];
}

export interface MonitorStatus {
  enabled: boolean;
  intervalSeconds: number;
  lastCheck: string | null;
}

export interface MonitorIntervalRequest {
  seconds: number;
}

export interface TimelineEntry {
  at: string;
  tipo: string;
  detalle: string;
}

export interface CaseReport {
  caseId: string;
  systemId: string;
  status: string;
  severity: string;
  category: string;
  priority: string;
  description: string;
  summary: string;
  probableCause: string;
  evidence: EvidenceEntry[];
  actionsExecuted: string;
  verificationResult: string;
  timeline: TimelineEntry[];
  generatedAt: string;
}

export interface EvidenceEntry {
  service: string;
  tipo: string;
  contenido: string;
}

export interface DiagnosisReport {
  systemId: string;
  problemDescription: string;
  generatedAt: string;
  probableCause: string;
  evidence: EvidenceEntry[];
  recommendedActions: string[];
}

export interface DiagnoseRequest {
  systemId: string;
  problemDescription: string;
}

export interface ScenarioInfo {
  id: ScenarioId;
  name: string;
  description: string;
  active: boolean;
}

export interface ScenarioResult {
  scenario: ScenarioId;
  injected: boolean;
  message: string;
}

export interface InjectRequest {
  service?: string;
  root?: string;
}
