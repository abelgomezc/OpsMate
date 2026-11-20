import axios, { AxiosInstance, AxiosError } from 'axios';
import type {
  Case,
  CreateCaseRequest,
  Approval,
  ApprovalDecision,
  SystemSummary,
  SystemHealth,
  MonitorStatus,
  DiagnoseRequest,
  ScenarioInfo,
  InjectRequest,
  ScenarioResult,
} from '../types';

const API_BASE = (import.meta as { env?: { VITE_API_BASE?: string } }).env?.VITE_API_BASE ?? '/api';

export const api: AxiosInstance = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

api.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    console.error('API error:', err.response?.status, err.message);
    return Promise.reject(err);
  },
);

/* ---------- Cases ---------- */
export const casesApi = {
  list: (params?: { status?: string[]; systemId?: string; severity?: string }) =>
    api.get<Case[]>('/cases', { params }),
  get: (id: string) => api.get<Case>(`/cases/${id}`),
  create: (body: CreateCaseRequest) => api.post<Case>('/cases', body),
  remove: (id: string) => api.delete<void>(`/cases/${id}`),
  events: (id: string) => new EventSource(`${API_BASE}/cases/${id}/events`),
};

/* ---------- Approvals ---------- */
export const approvalsApi = {
  pending: () => api.get<Approval[]>('/approvals/pending'),
  approve: (caseId: string, body: ApprovalDecision) =>
    api.post<void>(`/approvals/${caseId}/approve`, body),
  reject: (caseId: string, body: ApprovalDecision) =>
    api.post<void>(`/approvals/${caseId}/reject`, body),
};

/* ---------- Systems ---------- */
export const systemsApi = {
  list: () => api.get<SystemSummary[]>('/systems'),
  health: (id: string) => api.get<SystemHealth>(`/systems/${id}/health`),
};

/* ---------- Monitor ---------- */
export const monitorApi = {
  status: () => api.get<MonitorStatus>('/monitor/status'),
  start: () => api.post<MonitorStatus>('/monitor/start'),
  stop: () => api.post<MonitorStatus>('/monitor/stop'),
  interval: (seconds: number) =>
    api.post<MonitorStatus>('/monitor/interval', { seconds }),
};

/* ---------- Reports ---------- */
export const reportsApi = {
  exportCase: (caseId: string) =>
    api.get<string>(`/reports/case/${caseId}/export`, { responseType: 'text' }),
  system: (systemId: string) =>
    api.get<string>(`/reports/system/${systemId}`, { responseType: 'text' }),
  diagnose: (body: DiagnoseRequest) =>
    api.post<string>('/reports/diagnose', body, { responseType: 'text' }),
};

/* ---------- Sim ---------- */
export const simApi = {
  scenarios: () => api.get<ScenarioInfo[]>('/sim/scenarios'),
  inject: (scenarioId: string, body?: InjectRequest) =>
    api.post<ScenarioResult>(`/sim/scenarios/${scenarioId}/inject`, body ?? {}),
  clear: () => api.post<ScenarioResult>('/sim/scenarios/clear'),
};

export default api;
